package com.jdcosmetics.stockcollect.ui.scan

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.jdcosmetics.stockcollect.R
import com.jdcosmetics.stockcollect.databinding.DialogRechercheArticleBinding
import com.jdcosmetics.stockcollect.ui.session.ArticleRechercheAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RechercheArticleDialogFragment : DialogFragment() {

    private var _binding: DialogRechercheArticleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScanViewModel by activityViewModels()
    private lateinit var adapter: ArticleRechercheAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogRechercheArticleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeResults()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupRecyclerView() {
        adapter = ArticleRechercheAdapter { article ->
            dismiss()
            val codeBarre = article.codeBarrePrincipal ?: ""
            val action = ScanFragmentDirections.actionScanToResultat(codeBarre)
            findNavController().navigate(action)
        }
        binding.rvResultats.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResultats.adapter = adapter
    }

    private fun setupListeners() {
        binding.etRecherche.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.etRecherche.text?.toString()?.trim() ?: ""
                if (query.length >= 2) viewModel.rechercherArticles(query)
                true
            } else false
        }

        binding.etRecherche.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    viewModel.rechercherArticles(query)
                } else {
                    adapter.submitList(emptyList())
                    binding.tvEmpty.isVisible = false
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun observeResults() {
        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            adapter.submitList(results)
            binding.tvEmpty.isVisible = results.isEmpty()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
