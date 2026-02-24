package com.example.argeniecompanion.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.argeniecompanion.R;
import com.example.argeniecompanion.model.ChatMessage;

import java.util.List;

/**
 * Adapter for the Documents screen RecyclerView.
 * Receives a pre-filtered list of document-type messages — no filtering inside.
 */
public class DocumentsAdapter extends RecyclerView.Adapter<DocumentsAdapter.DocViewHolder> {

    public interface OnDocumentClickListener {
        void onDocumentClick(ChatMessage message);
    }

    private final List<ChatMessage> documents;
    @Nullable private final OnDocumentClickListener listener;

    DocumentsAdapter(List<ChatMessage> documents,
                     @Nullable OnDocumentClickListener listener) {
        this.documents = documents;
        this.listener  = listener;
    }

    @NonNull
    @Override
    public DocViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_document_list, parent, false);
        return new DocViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DocViewHolder holder, int position) {
        holder.bind(documents.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return documents.size();
    }

    // -------------------------------------------------------------------------

    static class DocViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout root;
        final ImageView    iconIv;
        final TextView     typeBadge;
        final TextView     nameTv;
        final TextView     senderTv;

        DocViewHolder(@NonNull View itemView) {
            super(itemView);
            root      = itemView.findViewById(R.id.doc_item_root);
            iconIv    = itemView.findViewById(R.id.doc_item_icon);
            typeBadge = itemView.findViewById(R.id.doc_item_type_badge);
            nameTv    = itemView.findViewById(R.id.doc_item_name);
            senderTv  = itemView.findViewById(R.id.doc_item_sender);
        }

        void bind(ChatMessage msg, @Nullable OnDocumentClickListener listener) {
            String mime  = msg.getMimeType();
            String label = msg.fileTypeLabel();

            // Icon
            if (mime.startsWith("image/")) {
                iconIv.setImageResource(R.drawable.photo_library_24px);
            } else if (mime.startsWith("video/")) {
                iconIv.setImageResource(R.drawable.video_library_24px);
            } else {
                iconIv.setImageResource(R.drawable.docs_24px);
            }

            // Type badge
            typeBadge.setText(label);

            // File name (use last URL path segment if available, fall back to full message)
            String rawMessage = msg.getMessage();
            String displayName = rawMessage;
            try {
                String segment = android.net.Uri.parse(rawMessage).getLastPathSegment();
                if (segment != null && !segment.isEmpty()) displayName = segment;
            } catch (Exception ignored) {}
            if (displayName.isEmpty()) displayName = label + " file";
            nameTv.setText(displayName);

            // Sender
            String sender = msg.getSender();
            senderTv.setText(sender.isEmpty() ? "Agent" : sender);

            // Click — whole row is the tap target
            root.setOnClickListener(v -> {
                if (listener != null) listener.onDocumentClick(msg);
            });
        }
    }
}
