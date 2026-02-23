package com.example.argeniecompanion.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.argeniecompanion.R;
import com.example.argeniecompanion.model.ChatMessage;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;
import java.util.stream.Collectors;

public class DocumentsBottomSheet extends BottomSheetDialogFragment {

    private final List<ChatMessage> allMessages;

    public DocumentsBottomSheet(List<ChatMessage> allMessages) {
        this.allMessages = allMessages;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_documents_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ImageView closeBtn = view.findViewById(R.id.docs_close_btn);
        TextView emptyTv = view.findViewById(R.id.docs_empty_tv);
        RecyclerView recyclerView = view.findViewById(R.id.docs_recycler_view);

        closeBtn.setOnClickListener(v -> dismiss());

        List<ChatMessage> docs = allMessages.stream()
                .filter(m -> !m.isTextMessage())
                .collect(Collectors.toList());

        if (docs.isEmpty()) {
            emptyTv.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyTv.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            recyclerView.setAdapter(new DocumentListAdapter(docs));
        }
    }

    // -------------------- INNER ADAPTER --------------------

    private static class DocumentListAdapter
            extends RecyclerView.Adapter<DocumentListAdapter.DocViewHolder> {

        private final List<ChatMessage> docs;

        DocumentListAdapter(List<ChatMessage> docs) {
            this.docs = docs;
        }

        @NonNull
        @Override
        public DocViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_document, parent, false);
            return new DocViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DocViewHolder holder, int position) {
            ChatMessage msg = docs.get(position);
            String label = msg.fileTypeLabel();

            holder.typeBadgeTv.setText(label);
            holder.nameTv.setText(msg.getMessage().isEmpty() ? label + " file" : msg.getMessage());
            holder.senderTv.setText(msg.getSender().isEmpty() ? "Agent" : msg.getSender());

            String mime = msg.getMimeType();
            if (mime.startsWith("image/")) {
                holder.iconIv.setImageResource(R.drawable.photo_library_24px);
            } else {
                holder.iconIv.setImageResource(R.drawable.docs_24px);
            }
        }

        @Override
        public int getItemCount() {
            return docs.size();
        }

        static class DocViewHolder extends RecyclerView.ViewHolder {
            final ImageView iconIv;
            final TextView typeBadgeTv;
            final TextView nameTv;
            final TextView senderTv;

            DocViewHolder(@NonNull View itemView) {
                super(itemView);
                iconIv = itemView.findViewById(R.id.doc_icon_iv);
                typeBadgeTv = itemView.findViewById(R.id.doc_type_badge_tv);
                nameTv = itemView.findViewById(R.id.doc_name_tv);
                senderTv = itemView.findViewById(R.id.doc_sender_tv);
            }
        }
    }
}
