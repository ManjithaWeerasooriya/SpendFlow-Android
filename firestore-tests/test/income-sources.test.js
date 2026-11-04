import assert from 'node:assert/strict';
import { EventEmitter, on } from 'node:events';
import { after, before, beforeEach, it } from 'node:test';
import { assertFails } from '@firebase/rules-unit-testing';
import { collection, doc, getDocFromServer, onSnapshot } from 'firebase/firestore';
import { ALICE, BOB, createDoc, createTestEnv, entities, updateDoc, validTransaction } from './helpers.js';

let env;
before(async () => { env = await createTestEnv(); });
beforeEach(async () => { await env.clearFirestore(); });
after(async () => { await env.cleanup(); });

const sourcePath = entities.categories.path(ALICE, 'salary');
const incomeSource = {
  name: 'Salary',
  type: 'INCOME',
  defaultCurrency: 'USD',
  iconName: null,
  colorArgb: null,
  sortOrder: 3,
  isArchived: false,
};

it('observes a source being created, renamed and archived without changing its historical transaction', async () => {
  const db = env.authenticatedContext(ALICE).firestore();
  const categories = collection(db, 'users/alice/categories');
  const source = doc(db, sourcePath);
  const historyPath = entities.transactions.path(ALICE, 'historical-income');
  const history = doc(db, historyPath);
  await createDoc(db, entities.categories.path(ALICE, 'food'), entities.categories.valid());

  const events = new EventEmitter();
  const snapshots = on(events, 'snapshot', { signal: AbortSignal.timeout(20_000) });
  const unsubscribe = onSnapshot(categories, { includeMetadataChanges: true }, (snapshot) => {
    if (!snapshot.metadata.fromCache && !snapshot.metadata.hasPendingWrites) events.emit('snapshot', snapshot);
  }, (error) => events.emit('error', error));

  const nextSnapshot = async (predicate) => {
    // There is one listener across the entire lifecycle. Ignore intermediate snapshots.
    for (;;) {
      const { value: [snapshot] } = await snapshots.next();
      if (predicate(snapshot.docs)) return snapshot.docs;
    }
  };
  const activeIncomeIds = (docs) => docs
    .filter((entry) => entry.get('type') === 'INCOME' && !entry.get('isArchived'))
    .map((entry) => entry.id);
  const observedSource = (docs) => docs.find((entry) => entry.id === source.id);

  try {
    const initial = await nextSnapshot((docs) => docs.some((entry) => entry.id === 'food'));
    assert.deepEqual(activeIncomeIds(initial), []);

    await createDoc(db, sourcePath, incomeSource);
    const created = await nextSnapshot((docs) => Boolean(observedSource(docs)));
    assert.deepEqual(activeIncomeIds(created), [source.id]);
    const original = (await getDocFromServer(source)).data();
    assert.deepEqual(
      { name: original.name, type: original.type, currency: original.defaultCurrency, archived: original.isArchived },
      { name: 'Salary', type: 'INCOME', currency: 'USD', archived: false },
    );

    await createDoc(db, historyPath, {
      ...validTransaction(), type: 'INCOME', categoryId: source.id, paymentMethod: null,
      amount: 12_500, currency: 'USD', rate: '300', baseAmount: 3_750_000, baseCurrency: 'LKR',
    });
    const historicalData = (await getDocFromServer(history)).data();

    // The repository updates the existing document with a merge, including the unchanged fields.
    const renamedSource = { ...incomeSource, name: 'Day job' };
    await updateDoc(db, sourcePath, renamedSource);
    const renamed = await nextSnapshot((docs) => observedSource(docs)?.get('name') === 'Day job');
    assert.deepEqual(activeIncomeIds(renamed), [source.id]);
    const renamedData = observedSource(renamed).data();
    assert.equal(renamedData.type, 'INCOME');
    assert.equal(renamedData.defaultCurrency, 'USD');
    assert.equal(renamedData.isArchived, false);
    assert.ok(renamedData.createdAt.isEqual(original.createdAt));

    await updateDoc(db, sourcePath, { ...renamedSource, isArchived: true });
    const archived = await nextSnapshot((docs) => observedSource(docs)?.get('isArchived') === true);
    assert.deepEqual(activeIncomeIds(archived), []);
    const stored = await getDocFromServer(source);
    assert.equal(stored.id, source.id);
    assert.equal(stored.get('name'), 'Day job');
    assert.equal(stored.get('type'), 'INCOME');
    assert.equal(stored.get('defaultCurrency'), 'USD');
    assert.equal(stored.get('isArchived'), true);
    assert.ok(stored.get('createdAt').isEqual(original.createdAt));

    const unchanged = (await getDocFromServer(history)).data();
    assert.equal(unchanged.categoryId, stored.id);
    assert.equal(unchanged.baseAmount, 3_750_000);
    assert.deepEqual(unchanged, historicalData);
  } finally {
    unsubscribe();
    await snapshots.return();
  }
});

it('allows duplicate source names under different IDs, but rejects invalid and unauthorized writes', async () => {
  const db = env.authenticatedContext(ALICE).firestore();
  const otherUser = env.authenticatedContext(BOB).firestore();
  const duplicate = entities.categories.path(ALICE, 'other-salary');
  await createDoc(db, sourcePath, incomeSource);
  await createDoc(db, duplicate, incomeSource);

  await assertFails(updateDoc(db, sourcePath, { ...incomeSource, name: '' }));
  await assertFails(updateDoc(db, sourcePath, { ...incomeSource, name: 'x'.repeat(101) }));
  await assertFails(updateDoc(db, sourcePath, { ...incomeSource, defaultCurrency: 'US' }));
  await assertFails(updateDoc(otherUser, sourcePath, { ...incomeSource, isArchived: true }));

  for (const path of [sourcePath, duplicate]) {
    const stored = await getDocFromServer(doc(db, path));
    assert.equal(stored.get('name'), 'Salary');
    assert.equal(stored.get('defaultCurrency'), 'USD');
    assert.equal(stored.get('isArchived'), false);
  }
});
