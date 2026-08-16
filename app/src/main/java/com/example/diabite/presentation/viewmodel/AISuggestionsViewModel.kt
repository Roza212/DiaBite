package com.example.diabite.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diabite.data.repository.GeminiRepository
import com.example.diabite.util.AppError
import com.example.diabite.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AISuggestionsViewModel @Inject constructor(
    private val geminiRepository: GeminiRepository
) : ViewModel() {

    private val _mealSuggestions = MutableStateFlow<Resource<String>>(Resource.loading())
    val mealSuggestions: StateFlow<Resource<String>> = _mealSuggestions

    fun generateMealSuggestions(userConditions: List<String>, diabetesType: String?) {
        println("DEBUG: AISuggestionsViewModel.generateMealSuggestions called with conditions: $userConditions, diabetesType: $diabetesType")
        viewModelScope.launch {
            _mealSuggestions.value = Resource.loading()

            try {
                geminiRepository.generateMealSuggestions(userConditions, diabetesType).collect { resource ->
                    println("DEBUG: Repository returned: $resource")
                    _mealSuggestions.value = resource
                }
            } catch (e: Exception) {
                println("DEBUG: Exception in ViewModel: ${e.message}")
                e.printStackTrace()
                _mealSuggestions.value = Resource.error(AppError.UnknownError("Failed to generate suggestions: ${e.message}"))
            }
        }
    }
}
