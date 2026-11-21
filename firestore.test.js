const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validUserHudStatePayload(uid, now) {
  return {
    userId: uid,
    displayName: "OPERATOR ALICE",
    themeId: "TRON",
    telemetryThemeId: "SYNC_HUD",
    soundEnabled: true,
    scanlinesEnabled: true,
    showKeyboard: false,
    glitchShaderEnabled: true,
    glitchLoadThreshold: 75,
    activeMacroDeckId: "recon",
    createdAt: now,
    updatedAt: now,
  };
}

function validHudProfilePayload(uid, now) {
  return {
    userId: uid,
    name: "NIGHT OPS",
    themeId: "MATRIX",
    telemetryThemeId: "MILITARY_HUD",
    glitchShaderEnabled: true,
    glitchLoadThreshold: 80,
    scanlinesEnabled: false,
    soundEnabled: true,
    activeDeckId: "kernel",
    builtIn: false,
    createdAt: now,
    updatedAt: now,
  };
}

function validMacroDeckPayload(uid, now) {
  return {
    userId: uid,
    name: "RECON",
    sortOrder: 0,
    macros: [
      {
        id: "m_ping_sel",
        label: "ping {ip}",
        commandTemplate: "ping {selected_ip}",
        requiresConfirm: false,
        accentRole: "SUCCESS",
      },
    ],
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated users cannot read or write any user state, profiles, or macro decks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validUserHudStatePayload(ALICE_UID, now))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("hud_profiles").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("macro_decks").get());
});

test("Authenticated owner: valid UserHudState create, partial update, and shadow/tamper rejections", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserHudStatePayload(ALICE_UID, created))
  );

  // Partial update of theme + activeHudProfileId succeeds when createdAt is preserved
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      themeId: "BLADE",
      activeHudProfileId: "prof_stealth_tron",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field (shadow update)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      themeId: "APOLLO",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Reject invalid enum
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      themeId: "INVALID_THEME",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Authenticated owner: hud_profiles and macro_decks CRUD, query alignment, and @DocumentId exclusion", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Profiles CRUD
  const profilesRef = aliceDb.collection("users").doc(ALICE_UID).collection("hud_profiles");
  await assertSucceeds(profilesRef.doc("prof_1").set(validHudProfilePayload(ALICE_UID, created)));
  await assertSucceeds(profilesRef.get());
  await assertSucceeds(
    profilesRef.doc("prof_1").update({
      name: "ALPHA RIG",
      glitchLoadThreshold: 65,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject @DocumentId 'id' inside write map
  await assertFails(
    profilesRef.doc("prof_bad").set({
      id: "prof_bad",
      ...validHudProfilePayload(ALICE_UID, created),
    })
  );

  // Reject out-of-range glitchLoadThreshold
  await assertFails(
    profilesRef.doc("prof_bad_num").set({
      ...validHudProfilePayload(ALICE_UID, created),
      glitchLoadThreshold: 120,
    })
  );

  // Macro decks CRUD
  const decksRef = aliceDb.collection("users").doc(ALICE_UID).collection("macro_decks");
  await assertSucceeds(decksRef.doc("recon").set(validMacroDeckPayload(ALICE_UID, created)));
  await assertSucceeds(decksRef.orderBy("sortOrder").get());
  await assertSucceeds(
    decksRef.doc("recon").update({
      macros: [
        {
          id: "m_ping_sel",
          label: "ping {ip}",
          commandTemplate: "ping {selected_ip}",
          requiresConfirm: true,
          accentRole: "WARNING",
        },
      ],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Owner can delete custom profile
  await assertSucceeds(profilesRef.doc("prof_1").delete());
});

test("Cross-user isolation: Bob cannot read or mutate Alice's HUD state, profiles, or macro decks", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const now = new Date(Date.now() - 10000);
    await db.collection("users").doc(ALICE_UID).set(validUserHudStatePayload(ALICE_UID, now));
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("hud_profiles")
      .doc("prof_1")
      .set(validHudProfilePayload(ALICE_UID, now));
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("macro_decks")
      .doc("recon")
      .set(validMacroDeckPayload(ALICE_UID, now));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ themeId: "BLADE", updatedAt: new Date(Date.now() - 500) })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("hud_profiles").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("hud_profiles").doc("prof_1").delete()
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("macro_decks").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("macro_decks").doc("recon").delete()
  );
});
