import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "لاله | جست‌وجوی گروه‌های عمومی",
  description: "کشف گروه‌های عمومی برق و صنعت بر اساس موضوع و شهر",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return <html lang="fa" dir="rtl"><body>{children}</body></html>;
}
