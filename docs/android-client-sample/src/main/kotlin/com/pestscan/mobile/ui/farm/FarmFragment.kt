package com.pestscan.mobile.ui.farm

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.pestscan.mobile.R
import kotlinx.coroutines.launch

class FarmFragment : Fragment(R.layout.fragment_farm) {
    private val viewModel: FarmViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = FarmAdapter()
        val recyclerView = view.findViewById<RecyclerView>(R.id.farm_list)
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    adapter.submitList(state.farms)
                    view.findViewById<View>(R.id.progress)
                        .isVisible = state.isLoading
                    view.findViewById<TextView>(R.id.error)
                        .text = state.error.orEmpty()
                }
            }
        }

        view.findViewById<SwipeRefreshLayout>(R.id.refresh)
            .setOnRefreshListener {
                viewModel.refresh()
            }
    }
}
