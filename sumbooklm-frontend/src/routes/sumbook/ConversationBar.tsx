import { Check, MessagesSquare, Plus, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import type { ChatSummary } from '@/chat/chatMessage';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

/**
 * The row that says which conversation is open and lets another one be chosen.
 *
 * A Sumbook can hold several conversations, and they are reached from a menu rather than from a list
 * beside the transcript. A fourth column would compete for the width of the panel that actually needs
 * it, and a reader works in one conversation at a time.
 *
 * The row disappears when a Sumbook holds no conversation at all. There is nothing to switch to, and
 * asking the first question is what creates one, so a control offering to start one would be a second
 * way to do what the field below already does.
 */
export function ConversationBar({
  conversations,
  currentId,
  onSelect,
  onStart,
  onRemove,
}: {
  conversations: ChatSummary[];
  currentId: string | null;
  onSelect: (sessionId: string) => void;
  onStart: () => void;
  onRemove: (sessionId: string) => void;
}) {
  const { t } = useTranslation();
  if (conversations.length === 0) {
    return null;
  }

  const current = conversations.find((conversation) => conversation.id === currentId);
  const name = (conversation: ChatSummary) =>
    conversation.title === '' ? t('sumbook.chat.untitled') : conversation.title;

  return (
    <div className="flex shrink-0 items-center gap-1">
      <span className="hidden max-w-40 truncate text-[0.8125rem] text-nb-muted sm:inline">
        {current ? name(current) : t('sumbook.chat.untitled')}
      </span>
      <DropdownMenu>
        <DropdownMenuTrigger
          aria-label={t('sumbook.chat.conversations')}
          className="flex size-9 shrink-0 items-center justify-center rounded-full text-nb-muted transition-colors outline-none hover:bg-nb-hover hover:text-nb-text focus-visible:ring-2 focus-visible:ring-nb-accent"
        >
          <MessagesSquare className="size-4" aria-hidden />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="w-64 rounded-nb-tile bg-nb-raised text-nb-text">
          {conversations.map((conversation) => (
            <DropdownMenuItem key={conversation.id} onClick={() => onSelect(conversation.id)}>
              <Check
                className={conversation.id === currentId ? 'opacity-100' : 'opacity-0'}
                aria-hidden
              />
              <span className="truncate">{name(conversation)}</span>
            </DropdownMenuItem>
          ))}
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={onStart}>
            <Plus />
            {t('sumbook.chat.newConversation')}
          </DropdownMenuItem>
          <DropdownMenuItem
            variant="destructive"
            disabled={currentId === null}
            onClick={() => currentId !== null && onRemove(currentId)}
          >
            <Trash2 />
            {t('sumbook.chat.deleteConversation')}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
