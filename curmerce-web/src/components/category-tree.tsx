"use client";

import type { PublicCategoryNode } from "@/lib/types/api";

interface CategoryTreeProps {
  categories: PublicCategoryNode[];
  selectedId: number | null;
  onSelect: (id: number | null) => void;
}

export function CategoryTree({ categories, selectedId, onSelect }: CategoryTreeProps) {
  return (
    <nav className="category-tree" aria-label="商品分类">
      <button
        className={`category-tree__item${selectedId === null ? " category-tree__item--active" : ""}`}
        type="button"
        onClick={() => onSelect(null)}
      >
        <span>全部商品</span>
        <span className="category-tree__count">↗</span>
      </button>
      {categories.map((category) => (
        <CategoryTreeNode key={category.id} category={category} selectedId={selectedId} onSelect={onSelect} />
      ))}
    </nav>
  );
}

function CategoryTreeNode({
  category,
  selectedId,
  onSelect,
}: {
  category: PublicCategoryNode;
  selectedId: number | null;
  onSelect: (id: number | null) => void;
}) {
  return (
    <div className="category-tree__branch">
      <button
        className={`category-tree__item${selectedId === category.id ? " category-tree__item--active" : ""}`}
        type="button"
        onClick={() => onSelect(category.id)}
      >
        <span>{category.name}</span>
        {category.children.length > 0 ? <span className="category-tree__count">{category.children.length}</span> : null}
      </button>
      {category.children.length > 0 ? (
        <div className="category-tree__children">
          {category.children.map((child) => (
            <CategoryTreeNode key={child.id} category={child} selectedId={selectedId} onSelect={onSelect} />
          ))}
        </div>
      ) : null}
    </div>
  );
}
