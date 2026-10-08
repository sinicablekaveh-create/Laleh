import Link from "next/link";
import { localCatalog } from "../../lib/local-catalog";
import { shouldPrefetchListItem } from "../../lib/navigation";

export default function CategoriesPage() {
  const categories = localCatalog.categories();
  return <main><header><Link className="brand" href="/">لاله</Link></header>
    <section className="hero"><h1>دسته‌بندی‌ها</h1><p>موضوع‌های موجود در فهرست عمومی تأییدشده</p></section>
    {categories.length ? <ul className="results">{categories.map(({ name, count }, index) => <li className="group" key={name}>
      <Link href={`/categories/${encodeURIComponent(name)}`} prefetch={shouldPrefetchListItem(index)}>{name}</Link> · {count.toLocaleString("fa-IR")} گروه
    </li>)}</ul> : <p className="empty">هنوز دسته‌بندی عمومی برای نمایش وجود ندارد.</p>}
  </main>;
}
