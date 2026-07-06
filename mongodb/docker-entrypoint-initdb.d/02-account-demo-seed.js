// Seeds the pre-filled "demo" account. This script is baked into the shared
// mongodb image, so it runs on every mongo container; the INIT_DUMP guard keeps
// it a no-op everywhere except account-mongodb, the only service that sets
// INIT_DUMP in docker-compose (preserving the legacy per-service seeding).
if (!process.env.INIT_DUMP) {
    print('INIT_DUMP not set; skipping demo account seed');
    quit(0);
}

const accounts = db.getSiblingDB('piggymetrics').accounts;

accounts.updateOne(
    { _id: 'demo' },
    {
        $set: {
            lastSeen: new Date(),
            note: 'demo note',
            expenses: [
                { amount: 1300, currency: 'USD', icon: 'home', period: 'MONTH', title: 'Rent' },
                { amount: 120, currency: 'USD', icon: 'utilities', period: 'MONTH', title: 'Utilities' },
                { amount: 20, currency: 'USD', icon: 'meal', period: 'DAY', title: 'Meal' },
                { amount: 240, currency: 'USD', icon: 'gas', period: 'MONTH', title: 'Gas' },
                { amount: 3500, currency: 'EUR', icon: 'island', period: 'YEAR', title: 'Vacation' },
                { amount: 30, currency: 'EUR', icon: 'phone', period: 'MONTH', title: 'Phone' },
                { amount: 700, currency: 'USD', icon: 'sport', period: 'YEAR', title: 'Gym' }
            ],
            incomes: [
                { amount: 42000, currency: 'USD', icon: 'wallet', period: 'YEAR', title: 'Salary' },
                { amount: 500, currency: 'USD', icon: 'edu', period: 'MONTH', title: 'Scholarship' }
            ],
            saving: {
                amount: 5900,
                capitalization: false,
                currency: 'USD',
                deposit: true,
                interest: 3.32
            }
        }
    },
    { upsert: true }
);

print('demo account seed complete');
