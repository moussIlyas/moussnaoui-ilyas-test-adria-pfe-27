import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

const API = '/api';

async function request(path, options = {}) {
  const response = await fetch(`${API}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(options.headers ?? {}) },
    ...options,
  });

  const text = await response.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = text;
    }
  }

  if (!response.ok) {
    const message =
      payload?.message ??
      payload?.detail ??
      (typeof payload === 'string' ? payload : null) ??
      `Erreur HTTP ${response.status}`;
    throw new Error(message);
  }
  return payload;
}

const money = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' });
const dateTime = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'short', timeStyle: 'short' });

const TYPE_LABELS = {
  PAYMENT: 'Paiement',
  TRANSFER_DEBIT: 'Virement émis',
  TRANSFER_CREDIT: 'Virement reçu',
};

const SUGGESTIONS = [
  'Liste mes comptes avec leur solde',
  'Quel est le solde du compte 11111111-1111-1111-1111-111111111111 ?',
  'Crée un paiement de 25 EUR sur le compte CUST-001',
];

export default function App() {
  const [tab, setTab] = useState('accounts');
  const [accounts, setAccounts] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [selectedAccountId, setSelectedAccountId] = useState('');

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [accountsResult, transactionsResult] = await Promise.all([
        request('/accounts'),
        request('/transactions'),
      ]);
      setAccounts(accountsResult ?? []);
      setTransactions(transactionsResult ?? []);
      setError(null);
    } catch (exception) {
      setError(exception.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const selectedAccount = useMemo(
    () => accounts.find((account) => account.id === selectedAccountId) ?? null,
    [accounts, selectedAccountId],
  );

  const totalBalance = useMemo(
    () => accounts.filter((account) => account.status === 'ACTIVE').reduce((sum, account) => sum + account.balance, 0),
    [accounts],
  );

  const handleOperation = async (kind, form) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const body = {
        amount: Number(form.amount),
        currency: form.currency,
        merchantCategory: form.merchantCategory || 'OTHER',
        country: form.country || 'FR',
      };

      if (kind === 'payment') {
        await request('/transactions', {
          method: 'POST',
          body: JSON.stringify({ ...body, accountId: form.accountId }),
        });
        setNotice(`Paiement de ${money.format(body.amount)} enregistré.`);
      } else {
        await request('/transfers', {
          method: 'POST',
          body: JSON.stringify({
            ...body,
            fromAccountId: form.fromAccountId,
            toAccountId: form.toAccountId,
          }),
        });
        setNotice(`Virement de ${money.format(body.amount)} effectué.`);
      }
      await loadData();
      setTab('transactions');
    } catch (exception) {
      setError(exception.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">E</span>
          <div>
            <strong>Ebank</strong>
            <small>Banque en ligne</small>
          </div>
        </div>
        <nav className="tabs" aria-label="Navigation principale">
          {[
            ['accounts', 'Comptes'],
            ['transactions', 'Transactions'],
            ['assistant', 'Assistant IA'],
          ].map(([id, label]) => (
            <button
              key={id}
              type="button"
              className={tab === id ? 'tab active' : 'tab'}
              onClick={() => setTab(id)}
            >
              {label}
            </button>
          ))}
        </nav>
        <button type="button" className="refresh" onClick={loadData} disabled={loading}>
          {loading ? 'Chargement…' : 'Actualiser'}
        </button>
      </header>

      {(error || notice) && (
        <div className={error ? 'banner error' : 'banner success'} role="status">
          <span>{error ?? notice}</span>
          <button type="button" onClick={() => { setError(null); setNotice(null); }} aria-label="Fermer">
            ×
          </button>
        </div>
      )}

      <main className="content">
        <section className="hero">
          <div>
            <p className="eyebrow">Tableau de bord</p>
            <h1>Vos finances, en temps réel.</h1>
            <p className="muted">
              Comptes, paiements, virements et assistant conversationnel — le tout derrière la gateway.
            </p>
          </div>
          <div className="kpi-row">
            <article className="kpi">
              <span>Solde total actif</span>
              <strong>{money.format(totalBalance)}</strong>
            </article>
            <article className="kpi">
              <span>Comptes</span>
              <strong>{accounts.length}</strong>
            </article>
            <article className="kpi">
              <span>Transactions</span>
              <strong>{transactions.length}</strong>
            </article>
          </div>
        </section>

        {tab === 'accounts' && (
          <AccountsView
            accounts={accounts}
            loading={loading}
            onSelect={setSelectedAccountId}
            selectedAccountId={selectedAccountId}
            onPay={(accountId) => {
              setSelectedAccountId(accountId);
              setTab('transactions');
            }}
          />
        )}

        {tab === 'transactions' && (
          <TransactionsView
            accounts={accounts}
            transactions={transactions}
            busy={busy}
            selectedAccount={selectedAccount}
            onOperation={handleOperation}
          />
        )}

        {tab === 'assistant' && <AssistantView />}
      </main>

      <footer className="footer">
        <span>Ebank · Spring Cloud · REST / GraphQL / MCP / Kafka</span>
        <span>Gateway : 9999 · Account : 8057 · Transaction : 8056</span>
      </footer>
    </div>
  );
}

function AccountsView({ accounts, loading, selectedAccountId, onSelect, onPay }) {
  if (loading && accounts.length === 0) {
    return <p className="empty">Chargement des comptes…</p>;
  }
  if (accounts.length === 0) {
    return <p className="empty">Aucun compte disponible.</p>;
  }

  return (
    <section>
      <div className="section-heading">
        <h2>Mes comptes</h2>
        <p className="muted">Cliquez sur un compte pour le sélectionner dans les opérations.</p>
      </div>
      <div className="account-grid">
        {accounts.map((account) => {
          const active = account.status === 'ACTIVE';
          return (
            <article
              key={account.id}
              className={`account-card ${selectedAccountId === account.id ? 'selected' : ''}`}
              onClick={() => onSelect(account.id)}
            >
              <div className="account-top">
                <span className="account-ref">{account.customerReference}</span>
                <span className={active ? 'badge badge-ok' : 'badge badge-blocked'}>
                  {active ? 'Actif' : 'Bloqué'}
                </span>
              </div>
              <p className="account-balance">{money.format(account.balance)}</p>
              <p className="account-meta">
                {account.currency} · {account.id.slice(0, 8)}…
              </p>
              <button
                type="button"
                className="ghost"
                disabled={!active}
                onClick={(event) => {
                  event.stopPropagation();
                  onPay(account.id);
                }}
              >
                {active ? 'Nouveau paiement' : 'Compte bloqué'}
              </button>
            </article>
          );
        })}
      </div>
    </section>
  );
}

function TransactionsView({ accounts, transactions, busy, selectedAccount, onOperation }) {
  const activeAccounts = accounts.filter((account) => account.status === 'ACTIVE');
  const [mode, setMode] = useState('payment');
  const [form, setForm] = useState({
    accountId: '',
    fromAccountId: '',
    toAccountId: '',
    amount: '',
    currency: 'EUR',
    merchantCategory: 'OTHER',
    country: 'FR',
  });

  useEffect(() => {
    if (selectedAccount) {
      setForm((current) => ({
        ...current,
        accountId: selectedAccount.id,
        fromAccountId: selectedAccount.id,
        currency: selectedAccount.currency,
      }));
    }
  }, [selectedAccount]);

  useEffect(() => {
    if (!form.accountId && activeAccounts.length > 0) {
      setForm((current) => ({
        ...current,
        accountId: activeAccounts[0].id,
        fromAccountId: activeAccounts[0].id,
        toAccountId: activeAccounts[1]?.id ?? '',
        currency: activeAccounts[0].currency,
      }));
    }
  }, [activeAccounts, form.accountId]);

  const set = (key) => (event) => {
    const value = event.target.value;
    setForm((current) => {
      const next = { ...current, [key]: value };
      if (key === 'accountId' || key === 'fromAccountId') {
        const account = accounts.find((item) => item.id === value);
        if (account) next.currency = account.currency;
      }
      return next;
    });
  };

  const submit = (event) => {
    event.preventDefault();
    if (!form.amount || Number(form.amount) <= 0) return;
    onOperation(mode, form);
  };

  return (
    <section className="transactions-layout">
      <form className="panel" onSubmit={submit}>
        <div className="section-heading">
          <h2>Nouvelle opération</h2>
        </div>
        <div className="segmented">
          <button type="button" className={mode === 'payment' ? 'active' : ''} onClick={() => setMode('payment')}>
            Paiement
          </button>
          <button type="button" className={mode === 'transfer' ? 'active' : ''} onClick={() => setMode('transfer')}>
            Virement
          </button>
        </div>

        {mode === 'payment' ? (
          <label>
            Compte à débiter
            <select value={form.accountId} onChange={set('accountId')} required>
              <option value="" disabled>Choisir un compte</option>
              {activeAccounts.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.customerReference} — {money.format(account.balance)}
                </option>
              ))}
            </select>
          </label>
        ) : (
          <>
            <label>
              Compte source
              <select value={form.fromAccountId} onChange={set('fromAccountId')} required>
                <option value="" disabled>Choisir un compte</option>
                {activeAccounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.customerReference} — {money.format(account.balance)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Compte destinataire
              <select value={form.toAccountId} onChange={set('toAccountId')} required>
                <option value="" disabled>Choisir un compte</option>
                {activeAccounts
                  .filter((account) => account.id !== form.fromAccountId)
                  .map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.customerReference} — {account.currency}
                    </option>
                  ))}
              </select>
            </label>
          </>
        )}

        <div className="form-row">
          <label>
            Montant
            <input
              type="number"
              min="0.01"
              step="0.01"
              max="1000"
              placeholder="250.00"
              value={form.amount}
              onChange={set('amount')}
              required
            />
          </label>
          <label>
            Devise
            <input value={form.currency} onChange={set('currency')} maxLength={3} required />
          </label>
        </div>

        <div className="form-row">
          <label>
            Catégorie
            <select value={form.merchantCategory} onChange={set('merchantCategory')}>
              <option value="OTHER">Autre</option>
              <option value="FOOD">Alimentation</option>
              <option value="TRAVEL">Voyage</option>
              <option value="SHOPPING">Achats</option>
              <option value="TRANSFER">Virement</option>
            </select>
          </label>
          <label>
            Pays
            <input value={form.country} onChange={set('country')} maxLength={2} />
          </label>
        </div>

        <p className="hint">Plafond configuré par le config-service : 1 000,00 € par transaction.</p>
        <button type="submit" className="primary" disabled={busy || activeAccounts.length === 0}>
          {busy ? 'Traitement…' : mode === 'payment' ? 'Enregistrer le paiement' : 'Effectuer le virement'}
        </button>
      </form>

      <section className="panel">
        <div className="section-heading">
          <h2>Historique</h2>
          <p className="muted">{transactions.length} transaction(s)</p>
        </div>
        {transactions.length === 0 ? (
          <p className="empty">Aucune transaction pour le moment.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Type</th>
                  <th>Compte</th>
                  <th className="right">Montant</th>
                </tr>
              </thead>
              <tbody>
                {transactions.slice(0, 25).map((transaction) => (
                  <tr key={transaction.id}>
                    <td>{dateTime.format(new Date(transaction.createdAt))}</td>
                    <td>
                      <span className={`type type-${transaction.type.toLowerCase()}`}>
                        {TYPE_LABELS[transaction.type] ?? transaction.type}
                      </span>
                    </td>
                    <td className="mono">{transaction.accountId.slice(0, 8)}…</td>
                    <td className="right mono">{money.format(transaction.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </section>
  );
}

function AssistantView() {
  const [messages, setMessages] = useState([
    {
      role: 'assistant',
      content:
        'Bonjour ! Je suis l’assistant Ebank. Demandez-moi vos soldes, vos transactions, ou faites un paiement / virement.',
    },
  ]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const conversationId = useRef(`web-${Math.random().toString(36).slice(2, 10)}`);
  const endRef = useRef(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending]);

  const send = async (event, preset) => {
    event?.preventDefault();
    const message = (preset ?? input).trim();
    if (!message || sending) return;

    setMessages((current) => [...current, { role: 'user', content: message }]);
    setInput('');
    setSending(true);
    try {
      const response = await request('/chat', {
        method: 'POST',
        body: JSON.stringify({ conversationId: conversationId.current, message }),
      });
      setMessages((current) => [
        ...current,
        { role: 'assistant', content: response?.answer ?? 'Aucune réponse reçue.' },
      ]);
    } catch (exception) {
      setMessages((current) => [
        ...current,
        { role: 'assistant', content: `Erreur : ${exception.message}`, error: true },
      ]);
    } finally {
      setSending(false);
    }
  };

  return (
    <section className="assistant-layout">
      <div className="panel chat">
        <div className="section-heading">
          <h2>Assistant bancaire</h2>
          <p className="muted">Agent AI connecté aux serveurs MCP account-service et transaction-service.</p>
        </div>

        <div className="chat-log">
          {messages.map((message, index) => (
            <div key={`${message.role}-${index}`} className={`bubble ${message.role} ${message.error ? 'error' : ''}`}>
              <span className="bubble-role">{message.role === 'user' ? 'Vous' : 'Assistant'}</span>
              <p>{message.content}</p>
            </div>
          ))}
          {sending && (
            <div className="bubble assistant typing">
              <span className="bubble-role">Assistant</span>
              <p>Réflexion en cours…</p>
            </div>
          )}
          <div ref={endRef} />
        </div>

        <form className="chat-input" onSubmit={send}>
          <input
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder="Ex. Quel est le solde de CUST-001 ?"
            aria-label="Message"
          />
          <button type="submit" className="primary" disabled={sending || !input.trim()}>
            Envoyer
          </button>
        </form>
      </div>

      <aside className="panel suggestions">
        <h3>Suggestions</h3>
        {SUGGESTIONS.map((suggestion) => (
          <button key={suggestion} type="button" className="ghost" onClick={(event) => send(event, suggestion)}>
            {suggestion}
          </button>
        ))}
        <p className="hint">
          L’agent peut lire les comptes et exécuter des paiements ou virements via les tools MCP.
        </p>
      </aside>
    </section>
  );
}
