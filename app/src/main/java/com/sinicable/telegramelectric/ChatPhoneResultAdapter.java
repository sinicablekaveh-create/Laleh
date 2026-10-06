package com.sinicable.telegramelectric;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter bridge for displaying extracted phone results in Laleh UI.
 * Keeps UI independent from TDLib extraction layers.
 */
public class ChatPhoneResultAdapter extends BaseAdapter {
    private final Context context;
    private final List<ChatPhoneSourceLocator> items = new ArrayList<>();

    public ChatPhoneResultAdapter(Context context) {
        this.context = context;
    }

    public void update(List<ChatPhoneResultViewModel> results) {
        items.clear();
        if (results != null) {
            for (ChatPhoneResultViewModel result : results) {
                if (result != null) {
                    items.addAll(result.getResults());
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ChatPhoneResultCardView card;
        if (convertView instanceof ChatPhoneResultCardView) {
            card = (ChatPhoneResultCardView) convertView;
        } else {
            card = new ChatPhoneResultCardView(context);
        }
        ChatPhoneSourceLocator item = items.get(position);
        card.bind(
                item.getPhone(),
                item.getChatTitle(),
                item.getMessageTime(),
                new ChatPhoneResultCardView.ActionListener() {
                    @Override
                    public void onOpenMessage() {
                        TelegramMessageSourceOpener.openMessage(
                                context,
                                item.getChatId(),
                                item.getMessageId()
                        );
                    }

                    @Override
                    public void onOpenTelegram() {
                        TelegramResultOpener.openPhone(context, item.getPhone());
                    }
                }
        );
        return card;
    }
}
