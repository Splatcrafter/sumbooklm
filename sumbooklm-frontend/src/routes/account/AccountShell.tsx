import type { ReactNode } from 'react';

/**
 * Centred frame shared by the login and the registration view.
 */
export function AccountShell({
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
    <div className="mx-auto flex w-full max-w-sm flex-col gap-6 py-10">
      <header className="flex flex-col gap-1">
        <h2 className="text-xl font-semibold tracking-tight">{title}</h2>
        <p className="text-sm text-muted-foreground">{subtitle}</p>
      </header>
      {children}
      <footer className="text-sm text-muted-foreground">{footer}</footer>
    </div>
  );
}
