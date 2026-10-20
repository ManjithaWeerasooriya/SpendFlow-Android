// AC3: writes with a missing required field, a non-positive amount or otherwise invalid data are rejected.
import { after, before, beforeEach, describe, it } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { Timestamp, deleteField, doc, serverTimestamp, setDoc } from 'firebase/firestore';
import { ALICE, createDoc, createTestEnv, entities, seed, updateDoc, without } from './helpers.js';

const MAX_AMOUNT = 100000000000000;

const requiredFields = {
  profile: ['baseCurrency'],
  accounts: ['name', 'type', 'balanceMinor', 'currencyCode', 'isArchived'],
  categories: ['name', 'type', 'isArchived'],
  transactions: ['type', 'amount', 'currency', 'rate', 'baseAmount', 'baseCurrency', 'dateTime'],
  budgets: ['name', 'limitMinor', 'currencyCode', 'period', 'startDate'],
  recurring: ['type', 'amount', 'currency', 'rate', 'baseAmount', 'baseCurrency', 'frequency', 'interval',
    'startDate', 'nextOccurrence', 'isActive'],
};

let env;
let db;

before(async () => {
  env = await createTestEnv();
});

after(async () => {
  await env.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  db = env.authenticatedContext(ALICE).firestore();
});

let nextId = 0;

/** Creates a new document of entity `name` with `changes` applied to its valid data. */
const create = (name, changes) => {
  const { path, valid } = entities[name];
  return createDoc(db, path(ALICE, `doc${nextId++}`), { ...valid(), ...changes });
};

for (const [name, { path, valid }] of Object.entries(entities)) {
  describe(`${name} schema`, () => {
    it('accepts a valid create', async () => {
      await assertSucceeds(createDoc(db, path(ALICE), valid()));
    });

    for (const field of requiredFields[name]) {
      it(`rejects a create missing ${field}`, async () => {
        await assertFails(createDoc(db, path(ALICE), without(valid(), field)));
      });

      it(`rejects an update removing ${field}`, async () => {
        await seed(env, path(ALICE), valid());
        await assertFails(updateDoc(db, path(ALICE), { [field]: deleteField() }));
      });
    }

    it('rejects an unknown field', async () => {
      await assertFails(createDoc(db, path(ALICE), { ...valid(), uid: ALICE }));
    });

    it('rejects client-side timestamps', async () => {
      const now = Timestamp.now();
      await assertFails(setDoc(doc(db, path(ALICE)), { ...valid(), createdAt: now, updatedAt: now }));
    });

    it('rejects a create without timestamps', async () => {
      await assertFails(setDoc(doc(db, path(ALICE)), valid()));
    });

    it('accepts a valid update', async () => {
      await seed(env, path(ALICE), valid());
      await assertSucceeds(updateDoc(db, path(ALICE), valid()));
    });

    it('rejects an update that changes createdAt', async () => {
      await seed(env, path(ALICE), valid());
      await assertFails(updateDoc(db, path(ALICE), { ...valid(), createdAt: serverTimestamp() }));
    });

    it('rejects an update that does not bump updatedAt', async () => {
      await seed(env, path(ALICE), valid());
      await assertFails(setDoc(doc(db, path(ALICE)), valid(), { merge: true }));
    });
  });
}

for (const name of ['transactions', 'recurring']) {
  describe(`${name} amounts and movement rules`, () => {
    for (const [label, amount] of [['zero', 0], ['negative', -100], ['above the maximum', MAX_AMOUNT + 1],
      ['fractional', 25.5], ['a string', '2500']]) {
      it(`rejects an amount that is ${label}`, async () => {
        await assertFails(create(name, { amount, baseAmount: amount }));
      });
    }

    it('accepts the smallest and largest amounts', async () => {
      await assertSucceeds(create(name, { amount: 1, baseAmount: 1 }));
      await assertSucceeds(create(name, { amount: MAX_AMOUNT, baseAmount: MAX_AMOUNT }));
    });

    it('rejects an update to a non-positive amount', async () => {
      await seed(env, entities[name].path(ALICE), entities[name].valid());
      await assertFails(updateDoc(db, entities[name].path(ALICE), { amount: 0, baseAmount: 0 }));
    });

    it('accepts a foreign-currency movement with its rate', async () => {
      await assertSucceeds(create(name, { amount: 1000, currency: 'USD', rate: '312.45', baseAmount: 312450 }));
    });

    it('accepts a zero base amount from rounding a tiny foreign amount', async () => {
      await assertSucceeds(create(name, { amount: 1, currency: 'JPY', rate: '0.004', baseAmount: 0 }));
    });

    it('rejects a negative base amount', async () => {
      await assertFails(create(name, { amount: 1000, currency: 'USD', rate: '312.45', baseAmount: -1 }));
    });

    for (const rate of ['0', '0.00', '-1.5', 'abc', '', '1e3']) {
      it(`rejects rate '${rate}'`, async () => {
        await assertFails(create(name, { currency: 'USD', rate, baseAmount: 2500 }));
      });
    }

    it('rejects a rate other than 1 in the base currency', async () => {
      await assertFails(create(name, { rate: '2', baseAmount: 5000 }));
    });

    it('rejects a base amount different from the amount in the base currency', async () => {
      await assertFails(create(name, { baseAmount: 2600 }));
    });

    it('rejects a lowercase or malformed currency code', async () => {
      await assertFails(create(name, { currency: 'usd', rate: '312.45', baseAmount: 781125 }));
      await assertFails(create(name, { baseCurrency: 'LK', currency: 'LK' }));
    });

    it('rejects an unknown type', async () => {
      await assertFails(create(name, { type: 'REFUND' }));
    });

    it('rejects an expense without a payment method', async () => {
      await assertFails(create(name, { paymentMethod: null }));
    });

    it('rejects an unknown payment method', async () => {
      await assertFails(create(name, { paymentMethod: 'CHEQUE' }));
    });

    it('rejects a payment method on income', async () => {
      await assertFails(create(name, { type: 'INCOME', paymentMethod: 'CASH' }));
      await assertSucceeds(create(name, { type: 'INCOME', paymentMethod: null }));
    });

    describe('transfers', () => {
      const transfer = { type: 'TRANSFER', paymentMethod: null, categoryId: null, accountId: 'acc1', transferAccountId: 'acc2' };

      it('accepts a transfer between two accounts', async () => {
        await assertSucceeds(create(name, transfer));
      });

      it('rejects a transfer to the same account', async () => {
        await assertFails(create(name, { ...transfer, transferAccountId: 'acc1' }));
      });

      it('rejects a transfer without a source or destination account', async () => {
        await assertFails(create(name, { ...transfer, accountId: null }));
        await assertFails(create(name, { ...transfer, transferAccountId: null }));
      });

      it('rejects a transfer with a category', async () => {
        await assertFails(create(name, { ...transfer, categoryId: 'cat1' }));
      });

      it('rejects a destination account on a non-transfer', async () => {
        await assertFails(create(name, { transferAccountId: 'acc2' }));
      });
    });

    it('rejects a note over 500 characters', async () => {
      await assertSucceeds(create(name, { note: 'x'.repeat(500) }));
      await assertFails(create(name, { note: 'x'.repeat(501) }));
    });

    it('rejects a non-string reference ID', async () => {
      await assertFails(create(name, { accountId: 42 }));
      await assertFails(create(name, { categoryId: '' }));
    });
  });
}

describe('transaction specifics', () => {
  it('rejects a dateTime that is not a timestamp', async () => {
    await assertFails(create('transactions', { dateTime: '2026-10-15T10:00:00Z' }));
  });

  it('rejects a document ID over 128 characters', async () => {
    await assertFails(createDoc(db, `users/${ALICE}/transactions/${'x'.repeat(129)}`, entities.transactions.valid()));
  });
});

describe('recurring specifics', () => {
  it('rejects an interval outside 1..365', async () => {
    await assertFails(create('recurring', { interval: 0 }));
    await assertFails(create('recurring', { interval: 366 }));
    await assertSucceeds(create('recurring', { interval: 365 }));
  });

  it('rejects an unknown frequency', async () => {
    await assertFails(create('recurring', { frequency: 'HOURLY' }));
  });

  it('rejects an end date before the start date', async () => {
    await assertFails(create('recurring', { endDate: '2026-09-30' }));
    await assertSucceeds(create('recurring', { endDate: '2026-10-01' }));
  });

  it('rejects a next occurrence before the start date', async () => {
    await assertFails(create('recurring', { nextOccurrence: '2026-09-30' }));
  });

  it('rejects malformed dates', async () => {
    await assertFails(create('recurring', { startDate: '01/10/2026' }));
    await assertFails(create('recurring', { endDate: '2026-1-1' }));
  });
});

describe('budget specifics', () => {
  for (const [label, limitMinor] of [['zero', 0], ['negative', -1], ['above the maximum', MAX_AMOUNT + 1]]) {
    it(`rejects a limit that is ${label}`, async () => {
      await assertFails(create('budgets', { limitMinor }));
    });
  }

  it('rejects an unknown period', async () => {
    await assertFails(create('budgets', { period: 'DAILY' }));
  });

  it('rejects a blank or too long name', async () => {
    await assertFails(create('budgets', { name: '' }));
    await assertFails(create('budgets', { name: 'x'.repeat(101) }));
  });

  it('rejects a malformed start date', async () => {
    await assertFails(create('budgets', { startDate: '2026-10' }));
  });
});

describe('account specifics', () => {
  it('accepts a negative balance (credit card debt)', async () => {
    await assertSucceeds(create('accounts', { type: 'CREDIT_CARD', balanceMinor: -150000 }));
  });

  it('rejects a balance beyond the maximum', async () => {
    await assertFails(create('accounts', { balanceMinor: -MAX_AMOUNT - 1 }));
  });

  it('rejects an unknown type', async () => {
    await assertFails(create('accounts', { type: 'CRYPTO' }));
  });

  it('rejects a non-boolean isArchived', async () => {
    await assertFails(create('accounts', { isArchived: 'false' }));
  });
});

describe('category specifics', () => {
  it('rejects an unknown type', async () => {
    await assertFails(create('categories', { type: 'TRANSFER' }));
  });

  it('rejects a color outside 32 bits', async () => {
    await assertFails(create('categories', { colorArgb: 4294967296 }));
  });

  it('accepts null optional fields', async () => {
    await assertSucceeds(create('categories', { iconName: null, colorArgb: null }));
  });
});

describe('profile specifics', () => {
  it('rejects a malformed email', async () => {
    await assertFails(create('profile', { email: 'not-an-email' }));
  });

  it('rejects a lowercase base currency', async () => {
    await assertFails(create('profile', { baseCurrency: 'lkr' }));
  });

  it('rejects changing the base currency', async () => {
    await seed(env, entities.profile.path(ALICE), entities.profile.valid());
    await assertFails(updateDoc(db, entities.profile.path(ALICE), { baseCurrency: 'USD' }));
    await assertSucceeds(updateDoc(db, entities.profile.path(ALICE), { displayName: 'Alice W' }));
  });
});
