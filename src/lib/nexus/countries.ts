export const COUNTRIES: Array<{ name: string; dial: string; code: string }> = [
  { name: "Australia", dial: "+61", code: "AU" },
  { name: "Bangladesh", dial: "+880", code: "BD" },
  { name: "Brazil", dial: "+55", code: "BR" },
  { name: "Canada", dial: "+1", code: "CA" },
  { name: "China", dial: "+86", code: "CN" },
  { name: "France", dial: "+33", code: "FR" },
  { name: "Germany", dial: "+49", code: "DE" },
  { name: "Hong Kong", dial: "+852", code: "HK" },
  { name: "India", dial: "+91", code: "IN" },
  { name: "Indonesia", dial: "+62", code: "ID" },
  { name: "Ireland", dial: "+353", code: "IE" },
  { name: "Italy", dial: "+39", code: "IT" },
  { name: "Japan", dial: "+81", code: "JP" },
  { name: "Kenya", dial: "+254", code: "KE" },
  { name: "Malaysia", dial: "+60", code: "MY" },
  { name: "Mexico", dial: "+52", code: "MX" },
  { name: "Nepal", dial: "+977", code: "NP" },
  { name: "Netherlands", dial: "+31", code: "NL" },
  { name: "New Zealand", dial: "+64", code: "NZ" },
  { name: "Nigeria", dial: "+234", code: "NG" },
  { name: "Pakistan", dial: "+92", code: "PK" },
  { name: "Philippines", dial: "+63", code: "PH" },
  { name: "Saudi Arabia", dial: "+966", code: "SA" },
  { name: "Singapore", dial: "+65", code: "SG" },
  { name: "South Africa", dial: "+27", code: "ZA" },
  { name: "South Korea", dial: "+82", code: "KR" },
  { name: "Spain", dial: "+34", code: "ES" },
  { name: "Sri Lanka", dial: "+94", code: "LK" },
  { name: "Sweden", dial: "+46", code: "SE" },
  { name: "Switzerland", dial: "+41", code: "CH" },
  { name: "Thailand", dial: "+66", code: "TH" },
  { name: "United Arab Emirates", dial: "+971", code: "AE" },
  { name: "United Kingdom", dial: "+44", code: "GB" },
  { name: "United States", dial: "+1", code: "US" },
  { name: "Vietnam", dial: "+84", code: "VN" },
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
