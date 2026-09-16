/** 自检面板结果项 */
export type CheckResult = {
  id: string;
  name: string;
  status: "PASS" | "FAIL" | "INFO";
  detail: string;
};
