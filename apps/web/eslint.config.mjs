import config from "@rayan/eslint-config";
const rules = [
  ...config,
  {
    ignores: [
      ".next/**",
      "playwright-report/**",
      "test-results/**",
      "next-env.d.ts",
    ],
  },
];
export default rules;
