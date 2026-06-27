// swissId, players, round
db.swiss_pairing.createIndex({ s: 1, p: 1, r: 1 });
// status
db.swiss_pairing.createIndex({ t: 1 }, { partialFilterExpression: { t: true } });

// swissId, score
db.swiss_player.createIndex({ s: 1, c: -1 });

db.swiss.createIndex({ teamId: 1, startsAt: 1 });
db.swiss.createIndex({ nextRoundAt: 1 }, { partialFilterExpression: { nextRoundAt: { $exists: true } } });
db.swiss.createIndex(
  { featurable: 1 },
  // settings: roundInterval
  { partialFilterExpression: { featurable: true, 'settings.i': { $lte: 600 } } }
);