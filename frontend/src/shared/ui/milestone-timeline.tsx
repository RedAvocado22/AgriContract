import { cn } from "@/shared/lib/utils"

type TimelineNodeStatus = "done" | "active" | "upcoming"

interface TimelineNode {
  key?: string
  title: string
  /** Dòng phụ, vd "28/05/2026 · Đã tất toán · 2.500.000.000 ₫". */
  meta: string
  status: TimelineNodeStatus
}

interface MilestoneTimelineProps {
  nodes: TimelineNode[]
  className?: string
}

function MilestoneTimeline({ nodes, className }: MilestoneTimelineProps) {
  return (
    <div data-slot="milestone-timeline" className={cn("flex flex-col", className)}>
      {nodes.map((node, index) => {
        const isLast = index === nodes.length - 1
        const isUpcoming = node.status === "upcoming"

        return (
          <div key={node.key ?? node.title} className="flex gap-3">
            <div className="flex flex-col items-center">
              <div
                className={cn(
                  "flex size-4 shrink-0 items-center justify-center rounded-full",
                  node.status === "done" && "bg-success text-white",
                  node.status === "active" && "border-2 border-info bg-surface",
                  isUpcoming && "bg-neutral"
                )}
              >
                {node.status === "done" ? <span className="ms text-[11px]">check</span> : null}
              </div>
              {!isLast ? <div className="mt-1 w-px flex-1 bg-border" /> : null}
            </div>
            <div className={cn("pb-5", isLast && "pb-0")}>
              <div
                className={cn(
                  "text-sm font-medium",
                  isUpcoming ? "text-text-muted" : "text-text-primary"
                )}
              >
                {node.title}
              </div>
              <div className={cn("text-[13px]", isUpcoming ? "text-text-muted" : "text-text-secondary")}>
                {node.meta}
              </div>
            </div>
          </div>
        )
      })}
    </div>
  )
}

export { MilestoneTimeline }
export type { TimelineNode, TimelineNodeStatus }
