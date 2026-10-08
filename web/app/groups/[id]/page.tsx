import Link from "next/link";
import { notFound } from "next/navigation";
import { localCatalog } from "../../../lib/local-catalog";

export default async function GroupPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const group = localCatalog.get(id);
  if (!group) notFound();
  return <main><header><Link className="brand" href="/">لاله</Link><Link href="/categories">دسته‌بندی‌ها</Link></header>
    <section className="hero"><h1>{group.title}</h1>
      <dl><dt>دسته‌بندی</dt><dd>{group.category || "تعیین نشده"}</dd>
        <dt>شهر</dt><dd>{group.location || "تعیین نشده"}</dd>
        <dt>نام عمومی</dt><dd dir="ltr">@{group.username}</dd></dl>
      <a href={`https://t.me/${group.username}`} target="_blank" rel="noopener noreferrer">باز کردن در تلگرام ↗</a>
      <p>اطلاعات عمومی ممکن است تغییر کند؛ پیش از عضویت، محتوای گروه را بررسی کنید.</p>
    </section></main>;
}
