import assert from 'node:assert/strict';
import { after, before, beforeEach, it } from 'node:test';
import { assertFails } from '@firebase/rules-unit-testing';
import { doc, getDocFromServer } from 'firebase/firestore';
import { ALICE, BOB, createDoc, createTestEnv, entities, updateDoc } from './helpers.js';

let env;
before(async () => { env = await createTestEnv(); });
beforeEach(async () => { await env.clearFirestore(); });
after(async () => { await env.cleanup(); });

it('persists classification, icon, order, archive and restore while retaining the category ID', async () => {
  const db = env.authenticatedContext(ALICE).firestore();
  const path = entities.categories.path(ALICE, 'food');
  const category = { ...entities.categories.valid(), iconName: 'food', classification: 'NEEDS', sortOrder: 0 };
  await createDoc(db, path, category);
  for (const classification of ['NEEDS', 'WANTS', 'SAVINGS', null]) {
    await updateDoc(db, path, { ...category, classification });
    assert.equal((await getDocFromServer(doc(db, path))).get('classification'), classification);
  }
  await updateDoc(db, path, { ...category, name: 'Groceries', sortOrder: 4, isArchived: true });
  const archived = await getDocFromServer(doc(db, path));
  assert.equal(archived.get('name'), 'Groceries');
  assert.equal(archived.get('isArchived'), true);
  assert.equal(archived.get('sortOrder'), 4);
  await updateDoc(db, path, { ...category, isArchived: false });
  assert.equal((await getDocFromServer(doc(db, path))).get('isArchived'), false);
  await assertFails(updateDoc(db, path, { ...category, classification: 'INVALID' }));
  await assertFails(updateDoc(db, path, { ...category, classification: 1 }));
  await assertFails(updateDoc(env.authenticatedContext(BOB).firestore(), path, category));
});
