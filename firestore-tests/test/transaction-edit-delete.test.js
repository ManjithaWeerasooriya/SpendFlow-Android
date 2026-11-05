import assert from 'node:assert/strict';
import { after, before, beforeEach, it } from 'node:test';
import { deleteDoc, doc, getDocFromServer, getDocsFromServer, collection } from 'firebase/firestore';
import { ALICE, createDoc, createTestEnv, entities, updateDoc, validTransaction } from './helpers.js';

let env;
before(async () => { env = await createTestEnv(); });
beforeEach(async () => { await env.clearFirestore(); });
after(async () => { await env.cleanup(); });

it('edits a foreign transaction in place with a new updatedAt and deletes it from totals', async () => {
  const db = env.authenticatedContext(ALICE).firestore();
  const path = entities.transactions.path(ALICE, 'stored');
  const original = { ...validTransaction(), type: 'INCOME', amount: 50000, currency: 'USD',
    rate: '300.123456', baseAmount: 15006173, baseCurrency: 'LKR', paymentMethod: null,
    categoryId: 'salary', note: 'Salary', recurringTransactionId: 'schedule' };
  await createDoc(db, path, original);
  const before = (await getDocFromServer(doc(db, path))).data();
  await updateDoc(db, path, { ...original, amount: 60000, baseAmount: 18007407, note: 'Corrected salary' });
  const saved = await getDocFromServer(doc(db, path));
  assert.equal(saved.id, 'stored');
  assert.equal(saved.get('currency'), 'USD');
  assert.equal(saved.get('rate'), original.rate);
  assert.equal(saved.get('recurringTransactionId'), original.recurringTransactionId);
  assert.ok(saved.get('createdAt').isEqual(before.createdAt));
  assert.ok(saved.get('updatedAt').toMillis() > before.updatedAt.toMillis());
  assert.equal(saved.get('amount'), 60000);
  assert.equal(saved.get('baseAmount'), 18007407);
  const total = async () => (await getDocsFromServer(collection(db, 'users/alice/transactions')))
    .docs.reduce((sum, row) => sum + row.get('baseAmount'), 0);
  assert.equal(await total(), 18007407);
  await deleteDoc(doc(db, path));
  assert.equal((await getDocFromServer(doc(db, path))).exists(), false);
  assert.equal(await total(), 0);
});
