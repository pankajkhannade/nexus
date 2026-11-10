export const COUNTRIES: Array<{ name: string; dial: string }> = [
  { name: "Australia", dial: "+61" },
  { name: "Bangladesh", dial: "+880" },
  { name: "Brazil", dial: "+55" },
  { name: "Canada", dial: "+1" },
  { name: "China", dial: "+86" },
  { name: "France", dial: "+33" },
  { name: "Germany", dial: "+49" },
  { name: "Hong Kong", dial: "+852" },
  { name: "India", dial: "+91" },
  { name: "Indonesia", dial: "+62" },
  { name: "Ireland", dial: "+353" },
  { name: "Italy", dial: "+39" },
  { name: "Japan", dial: "+81" },
  { name: "Kenya", dial: "+254" },
  { name: "Malaysia", dial: "+60" },
  { name: "Mexico", dial: "+52" },
  { name: "Nepal", dial: "+977" },
  { name: "Netherlands", dial: "+31" },
  { name: "New Zealand", dial: "+64" },
  { name: "Nigeria", dial: "+234" },
  { name: "Pakistan", dial: "+92" },
  { name: "Philippines", dial: "+63" },
  { name: "Saudi Arabia", dial: "+966" },
  { name: "Singapore", dial: "+65" },
  { name: "South Africa", dial: "+27" },
  { name: "South Korea", dial: "+82" },
  { name: "Spain", dial: "+34" },
  { name: "Sri Lanka", dial: "+94" },
  { name: "Sweden", dial: "+46" },
  { name: "Switzerland", dial: "+41" },
  { name: "Thailand", dial: "+66" },
  { name: "United Arab Emirates", dial: "+971" },
  { name: "United Kingdom", dial: "+44" },
  { name: "United States", dial: "+1" },
  { name: "Vietnam", dial: "+84" },
];

export function international(phone: string, dial: string) {
  const trimmed = phone.trim();
  if (!dial || /^\s*\+/.test(trimmed) || /^\s*00/.test(trimmed)) return trimmed;
  const digits = trimmed.replace(/\D/g, "");
  const code = dial.replace(/\D/g, "");
  if (!code || digits.length < 8 || digits.length > 11) return trimmed;
  if (digits.startsWith(code)) return `+${digits}`;
  return `+${code}${digits}`;
}
