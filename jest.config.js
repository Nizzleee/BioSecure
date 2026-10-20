/** @type {import('jest').Config} */
module.exports = {
  testEnvironment: "node",
  roots: ["<rootDir>/test"],
  testMatch: ["**/firestore_rules.test.ts"],
  testPathIgnorePatterns: ["/functions/", "/node_modules/", "/app/"],
  reporters: ["default"],
  transform: {
    "^.+\\.tsx?$": ["ts-jest", { tsconfig: "<rootDir>/tsconfig.json" }],
  },
};
