<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="@color/background">

    <!-- Header -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="60dp"
        android:orientation="horizontal"
        android:background="@color/sidebar_bg"
        android:paddingHorizontal="16dp"
        android:gravity="center_vertical">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="Minecraft Versions"
            android:textColor="@color/text"
            android:textSize="20sp"
            android:textStyle="bold"/>

        <ImageButton
            android:id="@+id/btnRefresh"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:src="@android:drawable/ic_menu_rotate"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:tint="@color/text_secondary"/>
    </LinearLayout>

    <!-- Swipe to Refresh + List -->
    <androidx.swiperefreshlayout.widget.SwipeRefreshLayout
        android:id="@+id/swipeRefresh"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <FrameLayout
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/recyclerView"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:padding="8dp"
                android:clipToPadding="false"/>

            <TextView
                android:id="@+id/emptyText"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Loading versions..."
                android:gravity="center"
                android:textColor="@color/text_muted"
                android:padding="32dp"
                android:visibility="gone"/>
        </FrameLayout>
    </androidx.swiperefreshlayout.widget.SwipeRefreshLayout>
</LinearLayout>
