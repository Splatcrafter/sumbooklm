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
    <div className="flex items-center justify-between gap-2 border-b border-jb-grey-80/50 pb-2">
      <span className="min-w-0 flex-1 truncate text-[0.8125rem] text-jb-grey-30">
        {current ? name(current) : t('sumbook.chat.untitled')}
      </span>
      <DropdownMenu>
        <DropdownMenuTrigger
          aria-label={t('sumbook.chat.conversations')}
          className="flex size-8 shrink-0 items-center justify-center rounded-jb-card text-jb-grey-50 transition-colors outline-none hover:bg-jb-grey-80/60 hover:text-jb-grey-10 focus-visible:ring-2 focus-visible:ring-jb-grey-30/40"
        >
          <MessagesSquare className="size-4" aria-hidden />
        </DropdownMenuTrigger>
        <DropdownMenuContent
          align="end"
          className="w-64 bg-jb-grey-90 text-jb-grey-10 ring-jb-grey-70/40"
        >
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
