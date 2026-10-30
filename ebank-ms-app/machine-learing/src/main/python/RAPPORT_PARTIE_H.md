# Partie H — Détection de fraude bancaire (Python)

Script exécutable répondant à la partie H de l'épreuve : classification binaire
`is_fraud` sur `bank_transactions.csv`.

## Contenu

| Fichier | Rôle |
|---|---|
| `fraud_detection.py` | Script principal (chargement → évaluation) |
| `requirements.txt` | Dépendances Python |
| `artifacts/metrics.json` | Toutes les métriques (JSON) |
| `artifacts/metrics.csv` | Tableau comparatif des modèles |
| `artifacts/model.joblib` | Pipeline retenu, réutilisable |
| `artifacts/01_eda.png` | Répartition des classes + valeurs manquantes |
| `artifacts/02_precision_recall.png` | Courbes précision-rappel des 3 modèles |
| `artifacts/03_confusion_matrix.png` | Matrice de confusion (modèle retenu) |
| `artifacts/04_threshold_sweep.png` | Effet du seuil (validation croisée) |
| `artifacts/05_feature_importance.png` | Importance des variables |

## Exécution

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python fraud_detection.py
```

Options : `--data`, `--output-dir`, `--test-size`, `--seed`.

## Démarche

1. **Chargement** du CSV, contrôle du schéma et typage de la cible.
2. **Analyse exploratoire** : 5 000 lignes, 257 fraudes (**5,14 %**), donc
   déséquilibre ≈ 1 fraude pour 19,5 transactions. Valeurs manquantes :
   `merchant_category` (84), `average_amount_last_7d` (75), `country` (66).
3. **Séparation stratifiée** 80/20 : 4 000 lignes d'entraînement (206 fraudes),
   1 000 de test (51 fraudes).
4. **Variables explicatives** : `amount`, `hour`, `transactions_last_24h`,
   `average_amount_last_7d`, `country`, `merchant_category`.
   `transaction_id`, `account_id` et la cible sont **exclus**.
5. **Pipeline** `ColumnTransformer` + `Pipeline` :
   - numériques : imputation médiane + `StandardScaler` ;
   - catégorielles : imputation par le mode + `OneHotEncoder(handle_unknown="ignore")`.
   Le pipeline complet est ajusté **uniquement** sur `x_train` (`fit` sur
   l'entraînement), ce qui évite toute fuite de données.
6. **Modèles testés** : régression logistique, arbre de décision, forêt aléatoire
   (`class_weight="balanced"` pour le déséquilibre). Sélection du modèle et du
   seuil par validation croisée stratifiée 5 folds (probabilités *out-of-fold*).
7. **Évaluation** sur le test : précision et rappel de la classe fraude, F1,
   matrice de confusion, *average precision*.

## Résultats

Validation croisée sur l'entraînement (sélection sans regarder le test) :

| Modèle | Average Precision | Seuil retenu | F1 fraude |
|---|---:|---:|---:|
| **Régression logistique** | **0,175** | 0,777 | 0,251 |
| Arbre de décision | 0,110 | 0,798 | 0,194 |
| Forêt aléatoire | 0,123 | 0,392 | 0,205 |

Évaluation sur les 1 000 transactions de test (51 fraudes) — modèle retenu :
régression logistique.

| Réglage | Précision (fraude) | Rappel (fraude) | F1 | AP | Accuracy |
|---|---:|---:|---:|---:|---:|
| Seuil 0,5 (défaut) | 0,122 | **0,627** | 0,205 | 0,189 | 0,751 |
| Seuil 0,777 (CV) | **0,208** | 0,196 | 0,202 | 0,189 | 0,921 |

Matrice de confusion au seuil retenu : 911 vrais négatifs, 38 faux positifs,
10 vrais positifs, 41 fraudes manquées.

Lecture honnête : l'AP (0,189) reste faible en valeur absolue, mais elle est
**≈ 3,7 fois supérieure à la prévalence** (0,051), ce qui montre un signal réel
mais limité dans les variables fournies. Le compromis seuil 0,5 ↔ seuil 0,777
illustre parfaitement les questions 2 et 3 : à 0,5 on détecte 63 % des fraudes
au prix de 230 fausses alertes ; à 0,777 on n'en détecte plus que 20 % avec
seulement 38 fausses alertes. Un seuil métier doit donc être choisi selon le
coût relatif d'une fraude manquée et d'une fausse alerte.

## Réponses aux questions d'analyse

### 1. Pourquoi l'accuracy seule est-elle insuffisante ?

Parce que les classes sont déséquilibrées (5,14 % de fraudes). Un modèle qui
prédit **toujours** « légitime » obtient 94,9 % d'accuracy sur le test sans
détecter une seule fraude. L'accuracy mélange les deux classes et récompense
la classe majoritaire ; elle ne dit rien sur la capacité à trouver les fraudes.
Il faut regarder la précision et le rappel de la classe fraude, le F1 et
l'*average precision*, qui est la métrique de référence sur des classes rares.

### 2. Quel compromis existe entre faux positifs et fraudes non détectées ?

C'est le compromis précision/rappel :

- **Faux positif** = transaction légitime bloquée → coût commercial (client
  mécontent, support, abandon), mais pas de perte directe.
- **Fraude non détectée** (faux négatif) = perte financière directe, souvent
  non récupérable, et risque de récidive.

Baisser le seuil augmente le rappel (plus de fraudes attrapées) mais multiplie
les faux positifs ; monter le seuil fait l'inverse. Le bon seuil dépend du coût
métier : ici, passer de 0,5 à 0,777 divise les fausses alertes par 6 (230 → 38)
mais fait chuter le rappel de 63 % à 20 %.

### 3. Quel est l'effet possible d'une diminution du seuil de décision ?

Le modèle alerte dès que la probabilité prédite dépasse le seuil. En le baissant :

- le **rappel augmente** (moins de fraudes manquées) ;
- la **précision diminue** (plus de fausses alertes) ;
- l'accuracy peut baisser alors que le modèle est plus utile à la détection ;
- le F1 atteint un maximum pour un seuil intermédiaire (illustré par
  `04_threshold_sweep.png`) ;
- un seuil trop bas rend le système inexploitable par les équipes de contrôle
  (saturation des alertes).

Le seuil est un **paramètre métier**, pas une propriété du modèle : il doit être
choisi sur des données de validation, pas sur le test.

### 4. Pourquoi privilégier une validation temporelle sur des données bancaires réelles ?

Une séparation aléatoire mélange passé et futur : une transaction de test peut
être « encadrée » par des transactions d'entraînement de la même période, ce qui
surestime la performance. En production, le modèle ne voit que le passé et doit
prédire des transactions futures, avec des comportements qui évoluent (nouveaux
commerçants, nouvelles techniques de fraude, dérive des montants). Une
validation temporelle (entraîner sur les N premiers mois, tester sur les mois
suivants, éventuellement en *walk-forward*) mesure la vraie capacité de
généralisation, détecte la dérive et évite de sélectionner un modèle qui exploite
des corrélations propres à la période d'entraînement. Ici le CSV ne contient
qu'une heure (`hour`) sans date, donc le split stratifié demandé est utilisé ;
en conditions réelles on utiliserait `createdAt` pour un split chronologique.
