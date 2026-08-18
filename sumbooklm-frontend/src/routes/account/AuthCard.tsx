import type { ReactNode } from 'react';

/**
 * Stacked cards that carry an account form.
 *
 * The form and the way out of it are two surfaces rather than one, which is how the JetBrains
 * website composes its blocks: the primary card holds the task, a lighter card below it holds what
 * comes next.
 *
 * Both surfaces are translucent so the layers behind them stay perceptible, which is what makes the
 * background part of the screen rather than wallpaper behind an opaque box. The double edge, a dark
 * border with a light inset hairline, is what keeps them readable on a moving backdrop.
 */
export function AuthCard({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-3">
      <section className="rounded-jb-block border border-jb-black/80 bg-jb-black/85 p-7 shadow-[0_28px_70px_-20px_rgb(0_0_0/0.85)] ring-1 ring-jb-grey-70/20 ring-inset backdrop-blur-2xl">
        <header className="flex flex-col gap-1.5">
          <h1 className="text-xl font-semibold tracking-tight text-jb-grey-5">{title}</h1>
          <p className="text-sm leading-relaxed text-jb-grey-50">{subtitle}</p>
        </header>
        <div className="mt-6">{children}</div>
      </section>
      <aside className="rounded-jb-card border border-jb-black/80 bg-jb-black/70 px-5 py-3.5 text-sm text-jb-grey-50 shadow-[0_16px_40px_-24px_rgb(0_0_0/0.8)] ring-1 ring-jb-grey-70/15 ring-inset backdrop-blur-xl">
        {footer}
      </aside>
    </div>
  );
}
