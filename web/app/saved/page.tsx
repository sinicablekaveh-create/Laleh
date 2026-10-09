import Link from "next/link";
import { DiscoveryPreferences, SavedGroups } from "../components/discovery-preferences";
export default function SavedPage() {
  return <main><header><Link className="brand" href="/">لاله</Link><Link href="/">جست‌وجو</Link></header>
    <h1>گروه‌های ذخیره‌شده</h1>
    <p>ذخیرهٔ یک گروه به معنی تأیید وضعیت فعلی یا عضویت در آن نیست؛ اطلاعات فعلی را در صفحهٔ گروه بررسی کن.</p>
    <SavedGroups /><DiscoveryPreferences />
  </main>;
}
