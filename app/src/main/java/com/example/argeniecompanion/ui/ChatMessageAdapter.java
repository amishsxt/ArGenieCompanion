package com.example.argeniecompanion.ui;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.argeniecompanion.R;
import com.example.argeniecompanion.model.ChatMessage;

import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_TEXT = 0;
    private static final int TYPE_DOCUMENT = 1;

    private final List<ChatMessage> messages;

    ChatMessageAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isTextMessage() ? TYPE_TEXT : TYPE_DOCUMENT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_TEXT) {
            View view = inflater.inflate(R.layout.item_chat_text, parent, false);
            return new TextViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_chat_document, parent, false);
            return new DocumentViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        if (holder instanceof TextViewHolder) {
            ((TextViewHolder) holder).bind(msg);
        } else if (holder instanceof DocumentViewHolder) {
            ((DocumentViewHolder) holder).bind(msg);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // -------------------- TEXT VIEW HOLDER --------------------

    static class TextViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout messageContainer;
        final LinearLayout senderRow;
        final TextView senderNameTv;
        final TextView messageTv;

        TextViewHolder(@NonNull View itemView) {
            super(itemView);
            messageContainer = itemView.findViewById(R.id.message_container);
            senderRow = itemView.findViewById(R.id.sender_row);
            senderNameTv = itemView.findViewById(R.id.sender_name_tv);
            messageTv = itemView.findViewById(R.id.message_tv);
        }

        void bind(ChatMessage msg) {
            messageTv.setText(msg.getMessage());

            if (msg.isFromUser()) {
                // User message: right-aligned, no sender header, blue bubble
                FrameLayout.LayoutParams params =
                        (FrameLayout.LayoutParams) messageContainer.getLayoutParams();
                params.gravity = Gravity.END;
                messageContainer.setLayoutParams(params);
                senderRow.setVisibility(View.GONE);
                messageTv.setBackgroundResource(R.drawable.message_bg_right);
            } else {
                // Agent message: left-aligned, show sender name, gray bubble
                FrameLayout.LayoutParams params =
                        (FrameLayout.LayoutParams) messageContainer.getLayoutParams();
                params.gravity = Gravity.START;
                messageContainer.setLayoutParams(params);
                senderRow.setVisibility(View.VISIBLE);
                senderNameTv.setText(msg.getSender().isEmpty() ? "Agent" : msg.getSender());
                messageTv.setBackgroundResource(R.drawable.message_bg);
            }
        }
    }

    // -------------------- DOCUMENT VIEW HOLDER --------------------

    static class DocumentViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout messageContainer;
        final LinearLayout senderRow;
        final TextView senderNameTv;
        final ImageView fileIconIv;
        final TextView fileNameTv;
        final TextView fileTypeTv;

        DocumentViewHolder(@NonNull View itemView) {
            super(itemView);
            messageContainer = itemView.findViewById(R.id.message_container);
            senderRow = itemView.findViewById(R.id.sender_row);
            senderNameTv = itemView.findViewById(R.id.sender_name_tv);
            fileIconIv = itemView.findViewById(R.id.file_icon_iv);
            fileNameTv = itemView.findViewById(R.id.file_name_tv);
            fileTypeTv = itemView.findViewById(R.id.file_type_tv);
        }

        void bind(ChatMessage msg) {
            String label = msg.fileTypeLabel();
            fileTypeTv.setText(label);
            fileNameTv.setText(msg.getMessage().isEmpty() ? label + " file" : msg.getMessage());

            // Icon based on type
            String mime = msg.getMimeType();
            if (mime.startsWith("image/")) {
                fileIconIv.setImageResource(R.drawable.photo_library_24px);
            }
            else if (mime.startsWith("video/")) {
                fileIconIv.setImageResource(R.drawable.video_library_24px);
            }
            else {
                fileIconIv.setImageResource(R.drawable.docs_24px);
            }

            if (msg.isFromUser()) {
                FrameLayout.LayoutParams params =
                        (FrameLayout.LayoutParams) messageContainer.getLayoutParams();
                params.gravity = Gravity.END;
                messageContainer.setLayoutParams(params);
                senderRow.setVisibility(View.GONE);
            } else {
                FrameLayout.LayoutParams params =
                        (FrameLayout.LayoutParams) messageContainer.getLayoutParams();
                params.gravity = Gravity.START;
                messageContainer.setLayoutParams(params);
                senderRow.setVisibility(View.VISIBLE);
                senderNameTv.setText(msg.getSender().isEmpty() ? "Agent" : msg.getSender());
            }
        }
    }
}
