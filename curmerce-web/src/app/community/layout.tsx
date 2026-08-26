import { CommunityTabs } from "@/components/community-tabs";

export default function CommunityLayout({ children }: { children: React.ReactNode }) {
  return <div className="community-shell"><CommunityTabs />{children}</div>;
}
