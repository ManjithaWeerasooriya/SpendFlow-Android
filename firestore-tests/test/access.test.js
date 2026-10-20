// AC1: a user can't read or write another user's data. AC2: unauthenticated requests are denied.
import { after, before, beforeEach, describe, it } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  orderBy,
  query,
  setDoc,
  Timestamp,
  where,
} from 'firebase/firestore';
import { ALICE, BOB, createDoc, createTestEnv, entities, seed, updateDoc } from './helpers.js';

const parentPath = (path) => path.slice(0, path.lastIndexOf('/'));

let env;

before(async () => {
  env = await createTestEnv();
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  for (const { path, valid } of Object.values(entities)) {
    await seed(env, path(ALICE), valid());
    await seed(env, path(BOB), valid());
  }
});

const callers = {
  'another user (AC1)': () => env.authenticatedContext(BOB).firestore(),
  'an unauthenticated caller (AC2)': () => env.unauthenticatedContext().firestore(),
};

for (const [caller, dbFor] of Object.entries(callers)) {
  describe(`${caller} accessing users/${ALICE}`, () => {
    for (const [name, { path, valid }] of Object.entries(entities)) {
      describe(name, () => {
        it('cannot get', async () => {
          await assertFails(getDoc(doc(dbFor(), path(ALICE))));
        });

        it('cannot list', async () => {
          await assertFails(getDocs(collection(dbFor(), parentPath(path(ALICE)))));
        });

        it('cannot create', async () => {
          await env.withSecurityRulesDisabled((ctx) => deleteDoc(doc(ctx.firestore(), path(ALICE))));
          await assertFails(createDoc(dbFor(), path(ALICE), valid()));
        });

        it('cannot update', async () => {
          await assertFails(updateDoc(dbFor(), path(ALICE), valid()));
        });

        it('cannot delete', async () => {
          await assertFails(deleteDoc(doc(dbFor(), path(ALICE))));
        });
      });
    }
  });
}

describe(`the owner accessing users/${ALICE}`, () => {
  const db = () => env.authenticatedContext(ALICE).firestore();

  for (const [name, { path, valid }] of Object.entries(entities)) {
    describe(name, () => {
      it('can get', async () => {
        await assertSucceeds(getDoc(doc(db(), path(ALICE))));
      });

      it('can create', async () => {
        await env.withSecurityRulesDisabled((ctx) => deleteDoc(doc(ctx.firestore(), path(ALICE))));
        await assertSucceeds(createDoc(db(), path(ALICE), valid()));
      });

      it('can update', async () => {
        await assertSucceeds(updateDoc(db(), path(ALICE), valid()));
      });

      it('can delete', async () => {
        await assertSucceeds(deleteDoc(doc(db(), path(ALICE))));
      });

      if (name !== 'profile') {
        it('can list', async () => {
          await assertSucceeds(getDocs(collection(db(), parentPath(path(ALICE)))));
        });
      }
    });
  }

  it('can run the app transaction date-range query', async () => {
    const q = query(
      collection(db(), `users/${ALICE}/transactions`),
      where('dateTime', '>=', Timestamp.fromDate(new Date('2026-10-01T00:00:00Z'))),
      where('dateTime', '<', Timestamp.fromDate(new Date('2026-11-01T00:00:00Z'))),
      orderBy('dateTime', 'desc'),
    );
    await assertSucceeds(getDocs(q));
  });

  it('works with an anonymous sign-in', async () => {
    const anon = env.authenticatedContext(ALICE, { firebase: { sign_in_provider: 'anonymous' } }).firestore();
    await assertSucceeds(getDoc(doc(anon, `users/${ALICE}/transactions/tx1`)));
    await assertFails(getDoc(doc(anon, `users/${BOB}/transactions/tx1`)));
  });
});

describe('default deny', () => {
  const db = () => env.authenticatedContext(ALICE).firestore();

  it('denies listing every user profile', async () => {
    await assertFails(getDocs(collection(db(), 'users')));
  });

  it('denies collection group queries across users', async () => {
    await assertFails(getDocs(collectionGroup(db(), 'transactions')));
  });

  it('denies an unknown sub-collection, even for the owner', async () => {
    await assertFails(setDoc(doc(db(), `users/${ALICE}/secrets/s1`), { value: 1 }));
    await assertFails(getDoc(doc(db(), `users/${ALICE}/secrets/s1`)));
  });

  it('denies an unknown top-level collection', async () => {
    await assertFails(setDoc(doc(db(), 'admin/config'), { value: 1 }));
    await assertFails(getDoc(doc(db(), 'admin/config')));
  });
});
