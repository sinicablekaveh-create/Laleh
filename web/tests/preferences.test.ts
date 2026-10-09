import { test } from "node:test";
import assert from "node:assert/strict";
import { emptyPreferences, parsePreferences, rememberQuery, toggleFavorite } from "../lib/preferences.ts";
const row = { schemaVersion: 2, groupId: "-1", title: "برق", username: "electric_group",
  category: "برق", location: "تهران", revision: "1" };
test("history is opt-in, bounded, normalized and revocable", () => {
  assert.deepEqual(rememberQuery(emptyPreferences(), "private query").history, []);
  let state = { ...emptyPreferences(), rememberHistory: true };
  for (let i = 0; i < 25; i++) state = rememberQuery(state, `كابل ${i}`);
  assert.equal(state.history.length, 20);
  state = rememberQuery(state, "كابل 24");
  assert.equal(state.history[0], "کابل 24");
  assert.deepEqual(parsePreferences(JSON.stringify({ ...state, rememberHistory: false })).history, []);
});
test("favorites accept only public records and toggle by stable decimal ID", () => {
  let state = toggleFavorite(emptyPreferences(), row);
  assert.equal(state.favorites.length, 1);
  assert.equal(toggleFavorite(state, { ...row, phone: "synthetic" }), state);
  state = toggleFavorite(state, row);
  assert.equal(state.favorites.length, 0);
});
test("corrupt, oversized and private persisted data fail closed", () => {
  assert.deepEqual(parsePreferences("{"), emptyPreferences());
  assert.deepEqual(parsePreferences("x".repeat(128001)), emptyPreferences());
  const value = parsePreferences(JSON.stringify({ ...emptyPreferences(), favorites: [{ ...row, session: "synthetic" }, row] }));
  assert.equal(value.favorites.length, 1);
});
