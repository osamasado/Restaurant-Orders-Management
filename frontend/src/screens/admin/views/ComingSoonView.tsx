import '../AdminScreen.css'

type ComingSoonViewProps = {
  title: string
}

/** Shared placeholder for admin sub-sections - issues #13-#16 replace these with real content. */
export function ComingSoonView({ title }: ComingSoonViewProps) {
  return (
    <div>
      <h2 className="coming-soon-view__title">{title}</h2>
      <p className="coming-soon-view__note">Coming soon</p>
    </div>
  )
}
