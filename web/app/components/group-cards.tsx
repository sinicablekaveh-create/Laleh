import Link from "next/link";
import type { SearchHit } from "../../lib/discovery-index";
import { shouldPrefetchListItem } from "../../lib/navigation";

export function GroupCards({ hits }: { hits: readonly SearchHit[] }) {
  return <div className="results">{hits.map(({ group }, index) => <article className="group" key={group.groupId}>
    <h3><Link href={`/groups/${group.groupId}`} prefetch={shouldPrefetchListItem(index)}>{group.title}</Link></h3>
    <p>{[group.category, group.location].filter(Boolean).join(" · ")}</p>
    <a href={`https://t.me/${group.username}`} target="_blank" rel="noopener noreferrer">مشاهده در تلگرام ↗</a>
  </article>)}</div>;
}
