// Creates the non-root application user that every PiggyMetrics data service
// authenticates as. Runs (via mongosh) against MONGO_INITDB_DATABASE, which is
// set to `piggymetrics` in docker-compose, so the user is created in that
// database and `piggymetrics` becomes its authentication source — matching the
// Spring Data MongoDB config (username: user, database: piggymetrics, no
// explicit authentication-database).
const password = process.env.MONGODB_PASSWORD;
if (!password) {
    throw new Error('MONGODB_PASSWORD not defined');
}

if (!db.getUser('user')) {
    db.createUser({
        user: 'user',
        pwd: password,
        roles: [{ role: 'readWrite', db: 'piggymetrics' }]
    });
    print('created application user "user" on piggymetrics');
} else {
    print('application user "user" already exists on piggymetrics');
}
