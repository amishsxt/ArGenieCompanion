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

public class ChatFragment extends Fragment {

    private static final String TAG = ChatFragment.class.getSimpleName();

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

    private RecyclerView recyclerView;
    private TextView noMessageTv;
    private ChatMessageAdapter adapter;
    private final List<ChatMessage> messages;

    public ChatFragment(List<ChatMessage> initialMessages) {
        this.messages = new ArrayList<>(initialMessages);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        ImageView backBtn = view.findViewById(R.id.chat_back_btn_iv);
        ImageView docsFilterBtn = view.findViewById(R.id.docs_filter_btn);
        recyclerView = view.findViewById(R.id.chatRecycleView);
        noMessageTv = view.findViewById(R.id.no_message_text_view);

        adapter = new ChatMessageAdapter(messages, this::openDocumentViewer);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        updateEmptyState();

        backBtn.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        docsFilterBtn.setOnClickListener(v -> {
            DocumentsBottomSheet sheet = new DocumentsBottomSheet(messages);
            sheet.show(requireActivity().getSupportFragmentManager(), "documents");
        });

        fetchChatHistory();

        return view;
    }

    // -------------------- HISTORY FETCH --------------------

    private void fetchChatHistory() {
        String sessionId = ArGenieApp.chatSessionId;
        if (sessionId == null) {
            AppLogger.w(TAG, "fetchChatHistory: chatSessionId is null, skipping");
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
                    AppLogger.e(TAG, "fetchChatHistory failed: " +
                            (error != null ? error.getMessage() : String.valueOf(responseError)));
                }
            });
        } catch (Exception e) {
            AppLogger.e(TAG, "fetchChatHistory error", e);
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

            // Collect IDs already in the list (arrived via MQTT before history loaded)
            Set<String> existingIds = new HashSet<>();
            for (ChatMessage m : messages) {
                existingIds.add(m.getMessageId());
            }

            List<ChatMessage> historical = new ArrayList<>();
            for (int i = 0; i < edges.length(); i++) {
                JSONObject node = edges.getJSONObject(i).optJSONObject("node");
                if (node == null) continue;
                ChatMessage msg = ChatMessage.fromGraphQlNode(node);
                if (!existingIds.contains(msg.getMessageId())) {
                    historical.add(msg);
                }
            }

            if (historical.isEmpty()) return;

            // Prepend history before any live MQTT messages
            messages.addAll(0, historical);
            adapter.notifyItemRangeInserted(0, historical.size());
            recyclerView.scrollToPosition(messages.size() - 1);
            updateEmptyState();

        } catch (JSONException e) {
            AppLogger.e(TAG, "parseHistoryResponse error", e);
        }
    }

    // -------------------- LIVE MQTT MESSAGES --------------------

    /** Called from MainActivity on the main thread when a new MQTT message arrives. */
    public void addMessage(ChatMessage message) {
        messages.add(message);
        if (adapter != null) {
            adapter.notifyItemInserted(messages.size() - 1);
            recyclerView.scrollToPosition(messages.size() - 1);
        }
        updateEmptyState();
    }

    // -------------------- DOCUMENT VIEWER --------------------

    private void openDocumentViewer(ChatMessage message) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new DocumentViewerFragment(message))
                .addToBackStack(null)
                .commit();
    }

    // -------------------- HELPERS --------------------

    private void updateEmptyState() {
        if (messages.isEmpty()) {
            noMessageTv.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            noMessageTv.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
}
