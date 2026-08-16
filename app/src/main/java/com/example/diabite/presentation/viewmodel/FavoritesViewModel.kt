package com.example.diabite.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diabite.data.model.FoodItem
import com.example.diabite.domain.repository.FoodRepository
import com.example.diabite.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val foodRepository: FoodRepository
) : ViewModel() {

    private val _favoriteFoods = MutableStateFlow<List<FoodItem>>(emptyList())
    val favoriteFoods: StateFlow<List<FoodItem>> = _favoriteFoods.asStateFlow()

    fun updateFavoriteFoods(favoriteIds: List<String>) {
        if (favoriteIds.isEmpty()) {
            _favoriteFoods.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                Timber.d("FavoritesViewModel.updateFavoriteFoods: fetching foods for ids: $favoriteIds")
                foodRepository.getFoodsByIds(favoriteIds).collect { res ->
                    when (res) {
                        is Resource.Success -> {
                            _favoriteFoods.value = res.data ?: emptyList()
                            Timber.d("FavoritesViewModel.updateFavoriteFoods: fetched ${_favoriteFoods.value.size} favorite foods")
                        }
                        is Resource.Error -> {
                            Timber.w("FavoritesViewModel.updateFavoriteFoods: error fetching foods: ${res.error?.userMessage}")
                            _favoriteFoods.value = emptyList()
                        }
                        else -> Timber.w("FavoritesViewModel.updateFavoriteFoods: unexpected resource state")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "FavoritesViewModel.updateFavoriteFoods: exception while fetching foods")
                _favoriteFoods.value = emptyList()
            }
        }
    }
}
