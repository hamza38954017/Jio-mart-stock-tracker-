package com.drdevhacks.jiomartmonitor.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.drdevhacks.jiomartmonitor.R;
import com.drdevhacks.jiomartmonitor.model.Product;
import com.drdevhacks.jiomartmonitor.model.StockResult;
import com.drdevhacks.jiomartmonitor.util.ProductStorage;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    public interface OnProductActionListener {
        void onDelete(Product product);
        void onEdit(Product product);
    }

    private final List<Product>           products;
    private final Context                 ctx;
    private       OnProductActionListener listener;

    public ProductAdapter(Context ctx, List<Product> products) {
        this.ctx      = ctx;
        this.products = products;
    }

    public void setListener(OnProductActionListener l) { this.listener = l; }
    public void updateResults() { notifyDataSetChanged(); }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_product_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Product     p = products.get(position);
        StockResult r = ProductStorage.getResult(ctx, p.getId());

        h.tvEmoji.setText(p.getEmoji());
        h.tvName.setText(p.getName());
        h.tvLocation.setText("📍 " + p.getLocationName());
        h.tvStores.setText("🏪 Stores: " + p.getStoreIdsString());
        resetNameStyle(h);

        if (r == null) {
            setStatus(h, "checking");
            h.tvQty.setText("—");
            h.tvPrice.setText("—");
            h.btnBuy.setVisibility(View.GONE);

        } else if (r.isError()) {
            setStatus(h, "error");
            h.tvQty.setText("Error");
            h.tvPrice.setText(r.getErrorMsg().length() > 30
                ? r.getErrorMsg().substring(0, 30) + "…" : r.getErrorMsg());
            h.btnBuy.setVisibility(View.GONE);

        } else if (r.isNotAvailableAtLocation()) {
            setStatus(h, "not_available");
            h.tvQty.setText("0 units");
            h.tvPrice.setText("—");
            h.btnBuy.setVisibility(View.GONE);

        } else if (r.isAvailable()) {
            setStatus(h, "in_stock");
            h.tvQty.setText("📦 " + r.getTotalQuantity() + " units");
            h.tvPrice.setText("💰 " + r.getPrice());
            h.btnBuy.setVisibility(View.VISIBLE);

            h.tvName.setTextColor(Color.parseColor("#66BB6A"));
            h.tvName.setClickable(true);
            h.tvName.setOnClickListener(v ->
                ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(p.getUrl()))));
            h.tvJioMartIcon.setVisibility(View.VISIBLE);
            h.tvJioMartIcon.setClickable(true);
            h.tvJioMartIcon.setOnClickListener(v ->
                ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(p.getUrl()))));
            h.tvOpenJiomart.setVisibility(View.VISIBLE);
            h.tvOpenJiomart.setClickable(true);
            h.tvOpenJiomart.setOnClickListener(v ->
                ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(p.getUrl()))));
            h.btnBuy.setOnClickListener(v ->
                ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(p.getUrl()))));

        } else {
            setStatus(h, "out_of_stock");
            h.tvQty.setText("0 units");
            h.tvPrice.setText("—");
            h.btnBuy.setVisibility(View.GONE);
        }

        if (r != null) {
            String time = new SimpleDateFormat("hh:mm a", Locale.getDefault())
                .format(new Date(r.getCheckedAt()));
            h.tvChecked.setText("Checked: " + time);
        } else {
            h.tvChecked.setText("Not checked yet");
        }

        if (!p.isDefault()) {
            h.btnDelete.setVisibility(View.VISIBLE);
            h.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(p); });
            h.btnEdit.setVisibility(View.VISIBLE);
            h.btnEdit.setOnClickListener(v -> { if (listener != null) listener.onEdit(p); });
        } else {
            h.btnDelete.setVisibility(View.GONE);
            h.btnEdit.setVisibility(View.GONE);
        }
    }

    private void resetNameStyle(@NonNull ViewHolder h) {
        h.tvName.setTextColor(Color.parseColor("#FFFFFF"));
        h.tvName.setClickable(false);
        h.tvName.setOnClickListener(null);
        h.tvJioMartIcon.setVisibility(View.GONE);
        h.tvOpenJiomart.setVisibility(View.GONE);
    }

    private void setStatus(@NonNull ViewHolder h, String status) {
        switch (status) {
            case "in_stock":
                h.card.setCardBackgroundColor(Color.parseColor("#1B2D1B"));
                h.tvStatus.setText("✅  IN STOCK");
                h.tvStatus.setTextColor(Color.parseColor("#66BB6A"));
                h.statusDot.setBackgroundResource(R.drawable.dot_green);
                break;
            case "out_of_stock":
                h.card.setCardBackgroundColor(Color.parseColor("#2D1B1B"));
                h.tvStatus.setText("❌  OUT OF STOCK");
                h.tvStatus.setTextColor(Color.parseColor("#EF5350"));
                h.statusDot.setBackgroundResource(R.drawable.dot_red);
                break;
            case "not_available":
                h.card.setCardBackgroundColor(Color.parseColor("#2A1F00"));
                h.tvStatus.setText("⚠️  NOT AVAILABLE AT YOUR LOCATION");
                h.tvStatus.setTextColor(Color.parseColor("#FFA000"));
                h.statusDot.setBackgroundResource(R.drawable.dot_orange);
                break;
            case "error":
                h.card.setCardBackgroundColor(Color.parseColor("#2D2A1B"));
                h.tvStatus.setText("⚠️  ERROR");
                h.tvStatus.setTextColor(Color.parseColor("#FFA726"));
                h.statusDot.setBackgroundResource(R.drawable.dot_orange);
                break;
            default:
                h.card.setCardBackgroundColor(Color.parseColor("#1C2233"));
                h.tvStatus.setText("⏳  CHECKING…");
                h.tvStatus.setTextColor(Color.parseColor("#78909C"));
                h.statusDot.setBackgroundResource(R.drawable.dot_grey);
                break;
        }
    }

    @Override public int getItemCount() { return products.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView card;
        TextView tvEmoji, tvName, tvLocation, tvStores, tvStatus,
                 tvQty, tvPrice, tvChecked, tvJioMartIcon, tvOpenJiomart;
        View     statusDot;
        Button   btnBuy, btnDelete, btnEdit;

        ViewHolder(View v) {
            super(v);
            card          = v.findViewById(R.id.card);
            tvEmoji       = v.findViewById(R.id.tvEmoji);
            tvName        = v.findViewById(R.id.tvName);
            tvLocation    = v.findViewById(R.id.tvLocation);
            tvStores      = v.findViewById(R.id.tvStores);
            tvStatus      = v.findViewById(R.id.tvStatus);
            tvQty         = v.findViewById(R.id.tvQty);
            tvPrice       = v.findViewById(R.id.tvPrice);
            tvChecked     = v.findViewById(R.id.tvChecked);
            tvJioMartIcon = v.findViewById(R.id.tvJioMartIcon);
            tvOpenJiomart = v.findViewById(R.id.tvOpenJiomart);
            statusDot     = v.findViewById(R.id.statusDot);
            btnBuy        = v.findViewById(R.id.btnBuy);
            btnDelete     = v.findViewById(R.id.btnDelete);
            btnEdit       = v.findViewById(R.id.btnEdit);
        }
    }
}
