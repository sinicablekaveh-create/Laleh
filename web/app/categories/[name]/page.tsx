import Link from "next/link";
import { notFound } from "next/navigation";
import { localCatalog } from "../../../lib/local-catalog";
import { GroupCards } from "../../components/group-cards";

export default async function CategoryPage({ params }: { params: Promise<{ name: string }> }) {
  const { name } = await params;
  const category = localCatalog.categories().find(row => row.name === name);
  if (!category) notFound();
  const result = localCatalog.search("", 0, 50, { category: name });
  return <main><header><Link className="brand" href="/">لاله</Link><Link href="/categories">همهٔ دسته‌بندی‌ها</Link></header>
    <section className="hero"><h1>{name}</h1><p>{category.count.toLocaleString("fa-IR")} گروه در این موضوع</p></section>
    <GroupCards hits={result.hits} /><p><Link href={`/?${new URLSearchParams({ category: name })}`}>جست‌وجو و صفحه‌بندی این موضوع</Link></p>
  </main>;
}
