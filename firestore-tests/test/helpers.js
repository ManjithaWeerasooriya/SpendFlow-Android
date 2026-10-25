import { readFileSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { Timestamp, doc, serverTimestamp, setDoc, setLogLevel } from 'firebase/firestore';

// The SDK logs every denied write as a warning; the deny tests expect those denials
setLogLevel('silent');

export const ALICE = 'alice';
export const BOB = 'bob';

// The emulator address comes from FIRESTORE_EMULATOR_HOST, which `npm run test:emulator` sets
export const createTestEnv = () =>
  initializeTestEnvironment({
    projectId: 'demo-spendflow',
    firestore: { rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8') },
  });

// Valid documents as the mappers write them (minus createdAt/updatedAt, which the write helpers add)

export const validUser = () => ({
  displayName: 'Alice',
  email: 'alice@example.com',
  baseCurrency: 'LKR',
});

export const validAccount = () => ({
  name: 'Wallet',
  type: 'CASH',
  balanceMinor: 500000,
  currencyCode: 'LKR',
  isArchived: false,
});

export const validCategory = () => ({
  name: 'Food',
  type: 'EXPENSE',
  iconName: 'restaurant',
  colorArgb: -16711936,
  isArchived: false,
});

const validMovement = () => ({
  type: 'EXPENSE',
  amount: 2500,
  currency: 'LKR',
  rate: '1',
  baseAmount: 2500,
  baseCurrency: 'LKR',
  paymentMethod: 'CASH',
  accountId: 'acc1',
  categoryId: 'cat1',
  transferAccountId: null,
  note: null,
});

export const validTransaction = () => ({
  ...validMovement(),
  dateTime: Timestamp.fromDate(new Date('2026-10-15T10:00:00Z')),
  recurringTransactionId: null,
});

export const validBudget = () => ({
  name: 'Groceries',
  limitMinor: 5000000,
  currencyCode: 'LKR',
  period: 'MONTHLY',
  startDate: '2026-10-01',
  categoryId: null,
});

export const validGoal = () => ({ name: 'Laptop', targetMinor: 15000000, savedMinor: 0, currencyCode: 'LKR' });

export const validRecurring = () => ({
  ...validMovement(),
  frequency: 'MONTHLY',
  interval: 1,
  startDate: '2026-10-01',
  endDate: null,
  nextOccurrence: '2026-11-01',
  isActive: true,
});

/** Every entity, with the path of one of its documents under users/{uid} (the profile has no id). */
export const entities = {
  profile: { path: (uid) => `users/${uid}`, valid: validUser },
  accounts: { path: (uid, id = 'acc1') => `users/${uid}/accounts/${id}`, valid: validAccount },
  categories: { path: (uid, id = 'cat1') => `users/${uid}/categories/${id}`, valid: validCategory },
  transactions: { path: (uid, id = 'tx1') => `users/${uid}/transactions/${id}`, valid: validTransaction },
  budgets: { path: (uid, id = 'bud1') => `users/${uid}/budgets/${id}`, valid: validBudget },
  goals: { path: (uid, id = 'goal1') => `users/${uid}/goals/${id}`, valid: validGoal },
  recurring: { path: (uid, id = 'rec1') => `users/${uid}/recurring/${id}`, valid: validRecurring },
};

/** A create as the app sends it: set() with server timestamps. */
export const createDoc = (db, path, data) =>
  setDoc(doc(db, path), { ...data, createdAt: serverTimestamp(), updatedAt: serverTimestamp() });

/** An update as the app sends it: set(merge) that bumps updatedAt and keeps createdAt. */
export const updateDoc = (db, path, data) =>
  setDoc(doc(db, path), { ...data, updatedAt: serverTimestamp() }, { merge: true });

/** Writes a valid stored document, bypassing the rules. */
export async function seed(env, path, data) {
  const stored = Timestamp.fromDate(new Date('2026-10-01T00:00:00Z'));
  await env.withSecurityRulesDisabled((ctx) =>
    setDoc(doc(ctx.firestore(), path), { ...data, createdAt: stored, updatedAt: stored }),
  );
}

export const without = (data, field) => {
  const { [field]: _, ...rest } = data;
  return rest;
};
