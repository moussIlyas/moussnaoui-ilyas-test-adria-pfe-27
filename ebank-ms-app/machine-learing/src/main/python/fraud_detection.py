#!/usr/bin/env python3
"""Partie H - IA avec Python : detection de fraude bancaire.

Classification binaire sur ``bank_transactions.csv`` (cible ``is_fraud``).

Principes respectes :
  * separation entrainement/test stratifiee ;
  * identifiants exclus des variables explicatives ;
  * pipeline unique (imputation + encodage + normalisation) ajuste
    UNIQUEMENT sur les donnees d'entrainement (via ``Pipeline``) ;
  * seuil de decision choisi par validation croisee sur l'entrainement,
    jamais sur l'ensemble de test.

Usage :
    python fraud_detection.py
    python fraud_detection.py --data chemin/vers/bank_transactions.csv \
                              --output-dir artifacts --test-size 0.2 --seed 42
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import joblib
import matplotlib

matplotlib.use("Agg")  # rendu sans affichage (serveur / CI)

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    ConfusionMatrixDisplay,
    accuracy_score,
    average_precision_score,
    classification_report,
    confusion_matrix,
    f1_score,
    precision_recall_curve,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.model_selection import StratifiedKFold, cross_val_predict, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from sklearn.tree import DecisionTreeClassifier

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

TARGET = "is_fraud"
ID_COLUMNS = ["transaction_id", "account_id"]
CATEGORICAL_FEATURES = ["country", "merchant_category"]
NUMERIC_FEATURES = [
    "amount",
    "hour",
    "transactions_last_24h",
    "average_amount_last_7d",
]

DEFAULT_DATA = (
    Path(__file__).resolve().parents[1] / "java" / "bank_transactions.csv"
)
DEFAULT_OUTPUT = Path(__file__).resolve().parent / "artifacts"

SEED = 42


# ---------------------------------------------------------------------------
# Chargement et analyse exploratoire
# ---------------------------------------------------------------------------


def load_data(path: Path) -> pd.DataFrame:
    """Charge le CSV et verifie la presence des colonnes attendues."""
    if not path.is_file():
        raise FileNotFoundError(f"Fichier introuvable : {path}")

    data = pd.read_csv(path)
    expected = set(ID_COLUMNS + CATEGORICAL_FEATURES + NUMERIC_FEATURES + [TARGET])
    missing_columns = expected - set(data.columns)
    if missing_columns:
        raise ValueError(f"Colonnes manquantes dans le CSV : {sorted(missing_columns)}")

    data[TARGET] = data[TARGET].astype(int)
    return data


def describe_dataset(data: pd.DataFrame) -> dict:
    """Proportion de fraudes, valeurs manquantes et valeurs extremes."""
    total = len(data)
    fraud_count = int(data[TARGET].sum())
    missing = data.isna().sum()
    missing = missing[missing > 0].sort_values(ascending=False)

    print("=" * 70)
    print("1. CHARGEMENT ET ANALYSE EXPLORATOIRE")
    print("=" * 70)
    print(f"Lignes            : {total}")
    print(f"Colonnes          : {len(data.columns)}")
    print(f"Fraudes           : {fraud_count} ({fraud_count / total:.2%})")
    print(f"Transactions saines: {total - fraud_count} ({(total - fraud_count) / total:.2%})")
    print(f"Deséquilibre      : 1 fraude pour {total / max(fraud_count, 1):.1f} transactions")
    print()
    print("Valeurs manquantes :")
    if missing.empty:
        print("  aucune")
    else:
        for column, count in missing.items():
            print(f"  {column:<28} {count:>4} ({count / total:.2%})")
    print()
    print("Statistiques des variables numeriques :")
    print(data[NUMERIC_FEATURES].describe().round(2).to_string())
    print()
    print("Modalites categoriques :")
    for column in CATEGORICAL_FEATURES:
        counts = data[column].value_counts(dropna=False)
        formatted = {("(manquant)" if pd.isna(k) else str(k)): int(v) for k, v in counts.items()}
        print(f"  {column}: {formatted}")
    print()

    return {
        "rows": total,
        "fraud_count": fraud_count,
        "fraud_ratio": fraud_count / total,
        "missing": {str(k): int(v) for k, v in missing.items()},
    }


def plot_eda(data: pd.DataFrame, output_dir: Path) -> None:
    """Sauvegarde les graphiques d'analyse exploratoire."""
    fig, axes = plt.subplots(1, 2, figsize=(12, 4))

    counts = data[TARGET].value_counts().sort_index()
    axes[0].bar(
        ["Légitime (0)", "Fraude (1)"],
        [counts.get(0, 0), counts.get(1, 0)],
        color=["#4c72b0", "#c44e52"],
    )
    axes[0].set_title("Répartition des classes")
    axes[0].set_ylabel("Nombre de transactions")
    for index, value in enumerate([counts.get(0, 0), counts.get(1, 0)]):
        axes[0].text(index, value, f"{value}\n({value / len(data):.1%})", ha="center", va="bottom")

    missing = data.isna().sum()
    missing = missing[missing > 0].sort_values()
    if missing.empty:
        axes[1].text(0.5, 0.5, "Aucune valeur manquante", ha="center", va="center")
        axes[1].set_axis_off()
    else:
        axes[1].barh(missing.index, missing.values, color="#dd8452")
        axes[1].set_title("Valeurs manquantes par colonne")
        axes[1].set_xlabel("Nombre de valeurs manquantes")

    fig.tight_layout()
    fig.savefig(output_dir / "01_eda.png", dpi=150)
    plt.close(fig)


# ---------------------------------------------------------------------------
# Pretraitement et modeles
# ---------------------------------------------------------------------------


def build_preprocessor() -> ColumnTransformer:
    """Imputation + encodage + normalisation, dans un seul transformateur.

    Le ``ColumnTransformer`` est place dans un ``Pipeline`` : il est donc
    ajuste uniquement sur les donnees d'entrainement (pas de fuite de
    donnees depuis l'ensemble de test).
    """
    numeric_pipeline = Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="median")),
            ("scaler", StandardScaler()),
        ]
    )
    categorical_pipeline = Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="most_frequent")),
            (
                "encoder",
                OneHotEncoder(handle_unknown="ignore", sparse_output=False),
            ),
        ]
    )
    return ColumnTransformer(
        transformers=[
            ("num", numeric_pipeline, NUMERIC_FEATURES),
            ("cat", categorical_pipeline, CATEGORICAL_FEATURES),
        ],
        verbose_feature_names_out=False,
    )


def build_models(seed: int = SEED) -> dict[str, Pipeline]:
    """Trois modeles simples, avec gestion du desequilibre des classes."""
    preprocessor = build_preprocessor()
    return {
        "logistic_regression": Pipeline(
            steps=[
                ("preprocessor", preprocessor),
                (
                    "classifier",
                    LogisticRegression(
                        max_iter=2000,
                        class_weight="balanced",
                        random_state=seed,
                    ),
                ),
            ]
        ),
        "decision_tree": Pipeline(
            steps=[
                ("preprocessor", build_preprocessor()),
                (
                    "classifier",
                    DecisionTreeClassifier(
                        max_depth=6,
                        min_samples_leaf=20,
                        class_weight="balanced",
                        random_state=seed,
                    ),
                ),
            ]
        ),
        "random_forest": Pipeline(
            steps=[
                ("preprocessor", build_preprocessor()),
                (
                    "classifier",
                    RandomForestClassifier(
                        n_estimators=400,
                        min_samples_leaf=5,
                        class_weight="balanced_subsample",
                        n_jobs=-1,
                        random_state=seed,
                    ),
                ),
            ]
        ),
    }


def out_of_fold_probabilities(
    model: Pipeline, x_train: pd.DataFrame, y_train: pd.Series, folds: int = 5, seed: int = SEED
) -> np.ndarray:
    """Probabilites de fraude obtenues par validation croisee (OOF).

    Ces probabilites servent a choisir le seuil de decision sans jamais
    regarder l'ensemble de test.
    """
    cv = StratifiedKFold(n_splits=folds, shuffle=True, random_state=seed)
    return cross_val_predict(model, x_train, y_train, cv=cv, method="predict_proba")[:, 1]


def select_threshold(y_true: np.ndarray, probabilities: np.ndarray) -> tuple[float, float]:
    """Seuil maximisant le F1 de la classe fraude sur les predictions OOF."""
    precision, recall, thresholds = precision_recall_curve(y_true, probabilities)
    # precision/recall ont un element de plus que thresholds
    f1_scores = 2 * precision[:-1] * recall[:-1] / np.clip(
        precision[:-1] + recall[:-1], 1e-12, None
    )
    best_index = int(np.nanargmax(f1_scores))
    return float(thresholds[best_index]), float(f1_scores[best_index])


def evaluate(
    y_true: np.ndarray, probabilities: np.ndarray, threshold: float
) -> dict:
    """Metriques orientees classe fraude (1)."""
    predictions = (probabilities >= threshold).astype(int)
    tn, fp, fn, tp = confusion_matrix(y_true, predictions, labels=[0, 1]).ravel()
    return {
        "threshold": round(float(threshold), 4),
        "accuracy": round(float(accuracy_score(y_true, predictions)), 4),
        "precision_fraud": round(float(precision_score(y_true, predictions, pos_label=1, zero_division=0)), 4),
        "recall_fraud": round(float(recall_score(y_true, predictions, pos_label=1, zero_division=0)), 4),
        "f1_fraud": round(float(f1_score(y_true, predictions, pos_label=1, zero_division=0)), 4),
        "average_precision": round(float(average_precision_score(y_true, probabilities)), 4),
        "roc_auc": round(float(roc_auc_score(y_true, probabilities)), 4),
        "true_negatives": int(tn),
        "false_positives": int(fp),
        "false_negatives": int(fn),
        "true_positives": int(tp),
    }


# ---------------------------------------------------------------------------
# Visualisations
# ---------------------------------------------------------------------------


def plot_precision_recall(curves: dict[str, np.ndarray], y_test: np.ndarray, output_dir: Path) -> None:
    fig, ax = plt.subplots(figsize=(6, 5))
    for name, probabilities in curves.items():
        precision, recall, _ = precision_recall_curve(y_test, probabilities)
        ap = average_precision_score(y_test, probabilities)
        ax.plot(recall, precision, label=f"{name} (AP = {ap:.3f})")
    baseline = float(np.mean(y_test))
    ax.axhline(baseline, linestyle="--", color="grey", label=f"Hasard (prévalence = {baseline:.3f})")
    ax.set_xlabel("Rappel (fraudes détectées)")
    ax.set_ylabel("Précision (fraudes parmi les alertes)")
    ax.set_title("Courbes précision-rappel — ensemble de test")
    ax.legend(loc="upper right", fontsize=8)
    fig.tight_layout()
    fig.savefig(output_dir / "02_precision_recall.png", dpi=150)
    plt.close(fig)


def plot_confusion(y_true: np.ndarray, probabilities: np.ndarray, threshold: float, name: str, output_dir: Path) -> None:
    predictions = (probabilities >= threshold).astype(int)
    matrix = confusion_matrix(y_true, predictions, labels=[0, 1])
    fig, ax = plt.subplots(figsize=(5, 4))
    ConfusionMatrixDisplay(matrix, display_labels=["Légitime", "Fraude"]).plot(
        ax=ax, cmap="Blues", colorbar=False, values_format="d"
    )
    ax.set_title(f"Matrice de confusion — {name}\n(seuil = {threshold:.3f})")
    fig.tight_layout()
    fig.savefig(output_dir / "03_confusion_matrix.png", dpi=150)
    plt.close(fig)


def plot_threshold_sweep(y_train: np.ndarray, probabilities: np.ndarray, chosen: float, output_dir: Path) -> None:
    """Effet du seuil sur precision / rappel / F1 (calculs OOF, hors test)."""
    thresholds = np.linspace(0.01, 0.99, 99)
    precisions, recalls, f1s, alerts = [], [], [], []
    for threshold in thresholds:
        predictions = (probabilities >= threshold).astype(int)
        alerts.append(predictions.sum())
        precisions.append(precision_score(y_train, predictions, pos_label=1, zero_division=0))
        recalls.append(recall_score(y_train, predictions, pos_label=1, zero_division=0))
        f1s.append(f1_score(y_train, predictions, pos_label=1, zero_division=0))

    fig, ax = plt.subplots(figsize=(7, 5))
    ax.plot(thresholds, precisions, label="Précision (fraude)")
    ax.plot(thresholds, recalls, label="Rappel (fraude)")
    ax.plot(thresholds, f1s, label="F1 (fraude)", linewidth=2)
    ax.axvline(chosen, color="black", linestyle="--", label=f"Seuil retenu = {chosen:.3f}")
    ax.set_xlabel("Seuil de décision")
    ax.set_ylabel("Score")
    ax.set_title("Compromis faux positifs / fraudes non détectées\n(validation croisée sur l'entraînement)")
    ax.legend()
    ax.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(output_dir / "04_threshold_sweep.png", dpi=150)
    plt.close(fig)


def plot_importance(model: Pipeline, output_dir: Path) -> None:
    """Importance des variables (random forest uniquement)."""
    classifier = model.named_steps["classifier"]
    if not hasattr(classifier, "feature_importances_"):
        return
    feature_names = model.named_steps["preprocessor"].get_feature_names_out()
    importances = pd.Series(classifier.feature_importances_, index=feature_names)
    top = importances.sort_values(ascending=False).head(15).sort_values()

    fig, ax = plt.subplots(figsize=(7, 5))
    top.plot.barh(ax=ax, color="#55a868")
    ax.set_title("Variables les plus importantes (forêt aléatoire)")
    ax.set_xlabel("Importance")
    fig.tight_layout()
    fig.savefig(output_dir / "05_feature_importance.png", dpi=150)
    plt.close(fig)


# ---------------------------------------------------------------------------
# Programme principal
# ---------------------------------------------------------------------------


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", type=Path, default=DEFAULT_DATA, help="Chemin du CSV")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT, help="Répertoire des résultats")
    parser.add_argument("--test-size", type=float, default=0.2, help="Part de l'ensemble de test")
    parser.add_argument("--seed", type=int, default=SEED, help="Graine aléatoire")
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)
    random_state = args.seed

    # 1. Chargement + analyse exploratoire ---------------------------------
    data = load_data(args.data)
    dataset_summary = describe_dataset(data)
    plot_eda(data, args.output_dir)

    # 2 / 4. Variables explicatives : identifiants et cible exclus ---------
    features = NUMERIC_FEATURES + CATEGORICAL_FEATURES
    x = data[features]
    y = data[TARGET]

    # 3. Separation stratifiee --------------------------------------------
    x_train, x_test, y_train, y_test = train_test_split(
        x, y, test_size=args.test_size, stratify=y, random_state=random_state
    )
    print("=" * 70)
    print("2. SEPARATION ENTRAINEMENT / TEST (stratifiée)")
    print("=" * 70)
    print(f"Entraînement : {len(x_train)} lignes — {int(y_train.sum())} fraudes ({y_train.mean():.2%})")
    print(f"Test         : {len(x_test)} lignes — {int(y_test.sum())} fraudes ({y_test.mean():.2%})")
    print(f"Variables    : {features}")
    print()

    # 5 / 6. Pipeline + entrainement, selection du seuil par CV -----------
    models = build_models(seed=random_state)
    oof: dict[str, np.ndarray] = {}
    cv_scores: dict[str, dict] = {}
    thresholds: dict[str, float] = {}

    print("=" * 70)
    print("3. VALIDATION CROISEE SUR L'ENTRAINEMENT (5 folds stratifiés)")
    print("=" * 70)
    print(f"{'Modèle':<22}{'AP (CV)':>10}{'Seuil (CV)':>13}{'F1 fraude (CV)':>17}")
    for name, model in models.items():
        probabilities = out_of_fold_probabilities(model, x_train, y_train, folds=5, seed=random_state)
        threshold, best_f1 = select_threshold(y_train.to_numpy(), probabilities)
        oof[name] = probabilities
        thresholds[name] = threshold
        cv_scores[name] = {
            "average_precision": round(float(average_precision_score(y_train, probabilities)), 4),
            "threshold": round(threshold, 4),
            "f1_fraud": round(best_f1, 4),
        }
        print(
            f"{name:<22}{cv_scores[name]['average_precision']:>10.4f}"
            f"{threshold:>13.3f}{best_f1:>17.4f}"
        )
    print()

    best_name = max(cv_scores, key=lambda key: cv_scores[key]["average_precision"])
    print(f"Modèle retenu (meilleure AP en validation croisée) : {best_name}")
    print()

    # 7. Evaluation sur l'ensemble de test --------------------------------
    print("=" * 70)
    print("4. EVALUATION SUR L'ENSEMBLE DE TEST")
    print("=" * 70)

    results: dict[str, dict] = {}
    test_probabilities: dict[str, np.ndarray] = {}
    for name, model in models.items():
        model.fit(x_train, y_train)
        probabilities = model.predict_proba(x_test)[:, 1]
        test_probabilities[name] = probabilities
        results[name] = {
            "au_seuil_0.5": evaluate(y_test.to_numpy(), probabilities, 0.5),
            "au_seuil_choisi_en_CV": evaluate(y_test.to_numpy(), probabilities, thresholds[name]),
            "seuil_retenu": round(thresholds[name], 4),
        }

    header = f"{'Modèle':<30}{'Seuil':>7}{'Précision':>11}{'Rappel':>9}{'F1':>8}{'AP':>8}{'Accuracy':>10}"
    print(header)
    print("-" * len(header))
    for name, values in results.items():
        for label, key in (("  défaut", "au_seuil_0.5"), ("  optimisé", "au_seuil_choisi_en_CV")):
            metrics = values[key]
            print(
                f"{name + label:<22}{metrics['threshold']:>7.3f}{metrics['precision_fraud']:>11.4f}"
                f"{metrics['recall_fraud']:>9.4f}{metrics['f1_fraud']:>8.4f}"
                f"{metrics['average_precision']:>8.4f}{metrics['accuracy']:>10.4f}"
            )
    print()

    selected = results[best_name]["au_seuil_choisi_en_CV"]
    print(f"\nModèle retenu : {best_name} au seuil {selected['threshold']:.3f}")
    print(
        f"  Fraudes détectées : {selected['true_positives']}/{selected['true_positives'] + selected['false_negatives']}"
        f"  |  Fausses alertes : {selected['false_positives']}"
        f"  |  Fraudes manquées : {selected['false_negatives']}"
    )
    print("\nRapport de classification détaillé (test, seuil retenu) :")
    print(
        classification_report(
            y_test,
            (test_probabilities[best_name] >= thresholds[best_name]).astype(int),
            target_names=["Légitime (0)", "Fraude (1)"],
            digits=4,
            zero_division=0,
        )
    )

    majority = float(1 - y_test.mean())
    print(f"Rappel — accuracy d'un modèle qui prédit toujours « légitime » : {majority:.4f}")
    print()

    # Visualisations -------------------------------------------------------
    plot_precision_recall(test_probabilities, y_test.to_numpy(), args.output_dir)
    plot_confusion(y_test.to_numpy(), test_probabilities[best_name], thresholds[best_name], best_name, args.output_dir)
    plot_threshold_sweep(y_train.to_numpy(), oof[best_name], thresholds[best_name], args.output_dir)
    plot_importance(models[best_name], args.output_dir)

    # Sauvegarde -----------------------------------------------------------
    joblib.dump(models[best_name], args.output_dir / "model.joblib")
    summary = {
        "dataset": dataset_summary,
        "features": features,
        "excluded": ID_COLUMNS + [TARGET],
        "train_size": int(len(x_train)),
        "test_size": int(len(x_test)),
        "cv": cv_scores,
        "selected_model": best_name,
        "test": results,
        "majority_class_accuracy": round(majority, 4),
    }
    with open(args.output_dir / "metrics.json", "w", encoding="utf-8") as handle:
        json.dump(summary, handle, indent=2, ensure_ascii=False)

    flat_rows = []
    for name, values in results.items():
        for setting in ("au_seuil_0.5", "au_seuil_choisi_en_CV"):
            flat_rows.append({"modele": name, "reglage": setting, **values[setting]})
    pd.DataFrame(flat_rows).to_csv(args.output_dir / "metrics.csv", index=False)

    print("=" * 70)
    print(f"Résultats enregistrés dans : {args.output_dir}")
    print("  metrics.json, metrics.csv, model.joblib, 01..05_*.png")
    print("=" * 70)


if __name__ == "__main__":
    main()
