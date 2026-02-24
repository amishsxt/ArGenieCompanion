package com.example.argeniecompanion.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.argeniecompanion.R;
import com.example.argeniecompanion.app.ArGenieApp;
import com.example.argeniecompanion.logger.AppLogger;
import com.example.argeniecompanion.model.ChatMessage;
import com.example.argeniecompanion.network.ApiRequestManager;
import com.example.argeniecompanion.network.callbacks.ApiAsyncResponseCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cz.msebera.android.httpclient.entity.StringEntity;

/**
 * Full-screen documents list fragment.
 *
 * <p>Filters all chat messages to document types (anything that is not text/plain),
 * fetches session history independently (same query as {@link ChatFragment}),
 * and displays them in a vertical RecyclerView optimised for Vuzix D-pad navigation.</p>
 */
public class DocumentsFragment extends Fragment {

    private static final String TAG = DocumentsFragment.class.getSimpleName();

    private static final String HISTORY_QUERY =
            "query sessionDetails($sessionId: String!) {" +
            "  sessionDetails(sessionId: $sessionId) {" +
            "    chatMessages {" +
            "      edges {" +
            "        node {" +
            "          message messageId mimeType sender senderType createdAt" +
            "        }" +
            "      }" +
            "    }" +
            "  }" +
            "}";

    private final List<ChatMessage> allMessages;

    /** Filtered list — only non-text messages. Owned by this fragment. */
    private final List<ChatMessage> documents = new ArrayList<>();

    private DocumentsAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyTv;
    private TextView countTv;
    private ImageView backBtn;

    public DocumentsFragment(List<ChatMessage> allMessages) {
        this.allMessages = allMessages;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_documents, container, false);

        backBtn = view.findViewById(R.id.docs_back_btn);
        recyclerView      = view.findViewById(R.id.docs_recycler_view);
        emptyTv           = view.findViewById(R.id.docs_empty_tv);
        countTv           = view.findViewById(R.id.docs_count_tv);

        backBtn.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        // Seed with already-received MQTT documents
        documents.clear();
        for (ChatMessage msg : allMessages) {
            if (!msg.isTextMessage()) documents.add(msg);
        }

        adapter = new DocumentsAdapter(documents, this::openDocumentViewer);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        updateEmptyState();

        // Fetch session history so pre-existing documents are shown
        fetchDocumentHistory();

        // Auto-focus the first item after layout so D-pad works immediately
        recyclerView.post(() -> {
            RecyclerView.ViewHolder first = recyclerView.findViewHolderForAdapterPosition(0);
            if (first != null) first.itemView.requestFocus();
        });

        return view;
    }

    // -------------------------------------------------------------------------
    // History fetch
    // -------------------------------------------------------------------------

    private void fetchDocumentHistory() {
        String sessionId = ArGenieApp.chatSessionId;
        if (sessionId == null) {
            AppLogger.w(TAG, "fetchDocumentHistory: chatSessionId is null, skipping");
            return;
        }

        try {
            JSONObject variables = new JSONObject();
            variables.put("sessionId", sessionId);

            JSONObject body = new JSONObject();
            body.put("query", HISTORY_QUERY);
            body.put("variables", variables);

            StringEntity entity = new StringEntity(body.toString());
            String url = ArGenieApp.getInstance().getConfig().getGraphqlUrl();

            if (ApiRequestManager.client == null) ApiRequestManager.init();

            ApiRequestManager.makeAsyncPostRequest(url, entity, new ApiAsyncResponseCallback() {
                @Override public void OnStart() {}

                @Override
                public void OnSuccess(JSONObject response) {
                    parseHistoryResponse(response);
                }

                @Override
                public void OnFailure(JSONObject responseError, Throwable error) {
                    AppLogger.e(TAG, "fetchDocumentHistory failed: " +
                            (error != null ? error.getMessage() : String.valueOf(responseError)));
                }
            });
        } catch (Exception e) {
            AppLogger.e(TAG, "fetchDocumentHistory error", e);
        }
    }

    private void parseHistoryResponse(JSONObject response) {
        if (!isAdded()) return;

        try {
            JSONArray edges = response
                    .getJSONObject("data")
                    .getJSONObject("sessionDetails")
                    .getJSONObject("chatMessages")
                    .getJSONArray("edges");

            // Deduplicate against documents already in the list (from MQTT)
            Set<String> existingIds = new HashSet<>();
            for (ChatMessage m : documents) existingIds.add(m.getMessageId());

            List<ChatMessage> historical = new ArrayList<>();
            for (int i = 0; i < edges.length(); i++) {
                JSONObject node = edges.getJSONObject(i).optJSONObject("node");
                if (node == null) continue;
                ChatMessage msg = ChatMessage.fromGraphQlNode(node);
                if (!msg.isTextMessage() && !existingIds.contains(msg.getMessageId())) {
                    historical.add(msg);
                }
            }

            if (historical.isEmpty()) return;

            // Prepend so history appears above any live MQTT docs
            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                documents.addAll(0, historical);
                adapter.notifyItemRangeInserted(0, historical.size());
                updateEmptyState();
                // Re-focus first item after history loads
                recyclerView.post(() -> {
                    RecyclerView.ViewHolder first =
                            recyclerView.findViewHolderForAdapterPosition(0);
                    if (first != null) first.itemView.requestFocus();
                });
            });

        } catch (JSONException e) {
            AppLogger.e(TAG, "parseHistoryResponse error", e);
        }
    }

    // -------------------------------------------------------------------------
    // Live updates from MainActivity
    // -------------------------------------------------------------------------

    /**
     * Called from MainActivity on the main thread when a new MQTT message arrives
     * and it is a document type.
     */
    public void addDocument(ChatMessage message) {
        documents.add(message);
        if (adapter != null) {
            adapter.notifyItemInserted(documents.size() - 1);
        }
        updateEmptyState();
    }

    // -------------------------------------------------------------------------
    // Document viewer
    // -------------------------------------------------------------------------

    private void openDocumentViewer(ChatMessage message) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new DocumentViewerFragment(message))
                .addToBackStack(null)
                .commit();
    }

    // -------------------------------------------------------------------------
    // Key handling (called from MainActivity.dispatchKeyEvent)
    // -------------------------------------------------------------------------

    /**
     * Returns true if the key was consumed (focus moved to back button).
     * Called before the event reaches any view so focus traversal is bypassed.
     */
    public boolean handleNavKey(int keyCode) {
        if (recyclerView == null) return false;

        android.view.View focused = requireActivity().getCurrentFocus();

        // Focus is on the back button — trap it so it can't escape to activity views
        if (focused == backBtn) {
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                // Return focus to first list item
                if (recyclerView.getVisibility() == View.VISIBLE) {
                    RecyclerView.ViewHolder first =
                            recyclerView.findViewHolderForAdapterPosition(0);
                    if (first != null) first.itemView.requestFocus();
                }
                return true;
            }
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                return true; // consume — nothing above back button
            }
            return false; // let CENTER (OK) through so back button click fires
        }

        // Focus is on item 0 in the list — UP moves to back button
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP && focused != null) {
            RecyclerView.ViewHolder vh = recyclerView.findContainingViewHolder(focused);
            if (vh != null && vh.getAdapterPosition() == 0) {
                backBtn.requestFocus();
                return true;
            }
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void updateEmptyState() {
        if (documents.isEmpty()) {
            emptyTv.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            countTv.setVisibility(View.GONE);
        } else {
            emptyTv.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            countTv.setText(String.valueOf(documents.size()));
            countTv.setVisibility(View.VISIBLE);
        }
    }
}
