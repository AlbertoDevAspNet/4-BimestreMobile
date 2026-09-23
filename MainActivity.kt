package com.example.aplicativodetempo

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.aplicativodetempo.api.RetrofitClient
import com.example.aplicativodetempo.data.WeatherResponse
import com.example.aplicativodetempo.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Locale

/**
 * Activity principal do aplicativo de previsão do tempo.
 * Responsável pela interface do usuário, escuta de eventos do botão de busca
 * e consumo da API OpenWeatherMap para exibir a temperatura e velocidade do vento.
 */
class MainActivity : AppCompatActivity() {

    // Configuração do ViewBinding para acesso seguro às Views do XML
    private lateinit var binding: ActivityMainBinding

    companion object {
        /**
         * CHAVE DE API DO OPENWEATHER
         * Para utilizar o aplicativo com dados reais, substitua a string abaixo
         * pela sua chave pessoal obtida gratuitamente em: https://openweathermap.org/api
         */
        private const val API_KEY = "70810cac48de4e9542110172e85434af"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Habilita a exibição edge-to-edge
        enableEdgeToEdge()

        // Infla o layout usando ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ajusta o padding para respeitar as barras de sistema (Status bar e Navigation bar)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Configura o evento de clique do botão de busca de clima
        setupSearchButton()
    }

    /**
     * Configura o listener do botão de busca e a ação do teclado.
     */
    private fun setupSearchButton() {
        binding.btnSearch.setOnClickListener {
            // Oculta o teclado virtual ao clicar no botão
            hideKeyboard()

            // Obtém e limpa o texto digitado pelo usuário
            val cityName = binding.etCityName.text?.toString()?.trim().orEmpty()

            // Valida se o usuário digitou o nome de uma cidade
            if (cityName.isEmpty()) {
                showError("Por favor, digite o nome de uma cidade para consultar.")
                return@setOnClickListener
            }

            // Inicia a requisição à API
            fetchWeatherData(cityName)
        }
    }

    /**
     * Realiza a chamada assíncrona para a API OpenWeather usando Coroutines.
     *
     * @param cityName Nome da cidade fornecida pelo usuário
     */
    private fun fetchWeatherData(cityName: String) {
        // Exibe o carregamento e esconde resultados anteriores
        showLoading(true)

        // Verifica se a chave de API ainda é a chave de exemplo padrão
        if (API_KEY == "INSIRA_SUA_CHAVE_OPENWEATHER_AQUI") {
            showLoading(false)
            showError("Atenção: Você precisa inserir sua chave de API do OpenWeather em MainActivity.kt (constante API_KEY) para buscar dados reais.")
            return
        }

        // Executa a requisição em uma Coroutine dentro do escopo do ciclo de vida da Activity
        lifecycleScope.launch {
            try {
                // Chamada à API através do Retrofit Client
                val response = RetrofitClient.api.getWeatherByCity(
                    cityName = cityName,
                    apiKey = API_KEY,
                    units = "metric",  // Traz a temperatura em Celsius e vento em m/s
                    lang = "pt_br"     // Traz a descrição do tempo em português
                )

                showLoading(false)

                if (response.isSuccessful) {
                    val weatherData = response.body()
                    if (weatherData != null) {
                        // Exibe os dados do clima na tela
                        displayWeatherData(weatherData)
                    } else {
                        showError("Resposta da API vazia ou inválida.")
                    }
                } else {
                    // Trata códigos de erro retornados pela API (ex: 404 - Cidade não encontrada)
                    when (response.code()) {
                        404 -> showError("Cidade \"$cityName\" não encontrada. Verifique a grafia e tente novamente.")
                        401 -> showError("Chave de API inválida ou não ativada. Verifique sua chave no OpenWeather.")
                        else -> showError("Erro ao buscar dados do clima (Código: ${response.code()}).")
                    }
                }
            } catch (e: IOException) {
                // Trata erros de falta de conexão com a internet ou timeout
                showLoading(false)
                showError("Erro de conexão. Verifique sua conexão com a internet e tente novamente.")
            } catch (e: Exception) {
                // Trata outros erros inesperados
                showLoading(false)
                showError("Ocorreu um erro inesperado: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Preenche os componentes do XML com as informações recebidas da API.
     *
     * @param data Dados do clima obtidos do OpenWeather
     */
    private fun displayWeatherData(data: WeatherResponse) {
        // Esconde card de erro
        binding.cardError.visibility = View.GONE

        // Nome da cidade e país
        val city = data.cityName ?: "Cidade Desconhecida"
        val country = data.sys?.country ?: ""
        binding.tvCityName.text = if (country.isNotEmpty()) "$city, $country" else city

        // Descrição do clima (ex: "céu limpo", "chuva moderada")
        val description = data.weather?.firstOrNull()?.description ?: "Sem informação"
        // Capitaliza a primeira letra da descrição
        binding.tvWeatherDescription.text = description.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }

        // Temperatura formatada em °C
        val temp = data.main?.temp
        if (temp != null) {
            binding.tvTemperature.text = String.format(Locale.getDefault(), "%.0f°C", temp)
        } else {
            binding.tvTemperature.text = "--°C"
        }

        // Velocidade do vento: a API em "metric" retorna em m/s.
        // Convertemos também para km/h (multiplicando por 3.6) para melhor legibilidade.
        val windSpeedMs = data.wind?.speed
        if (windSpeedMs != null) {
            val windSpeedKmh = windSpeedMs * 3.6
            binding.tvWindSpeed.text = String.format(Locale.getDefault(), "%.1f km/h", windSpeedKmh)
        } else {
            binding.tvWindSpeed.text = "--"
        }

        // Sensação térmica
        val feelsLike = data.main?.feelsLike
        if (feelsLike != null) {
            binding.tvFeelsLike.text = String.format(Locale.getDefault(), "%.0f°C", feelsLike)
        } else {
            binding.tvFeelsLike.text = "--"
        }

        // Umidade do ar
        val humidity = data.main?.humidity
        if (humidity != null) {
            binding.tvHumidity.text = "$humidity%"
        } else {
            binding.tvHumidity.text = "--"
        }

        // Exibe o card principal com todos os resultados
        binding.cardResult.visibility = View.VISIBLE
    }

    /**
     * Altera a visibilidade do indicador de progresso (ProgressBar).
     */
    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.cardResult.visibility = View.GONE
            binding.cardError.visibility = View.GONE
        } else {
            binding.progressBar.visibility = View.GONE
        }
    }

    /**
     * Exibe uma mensagem de erro no card apropriado do XML.
     */
    private fun showError(message: String) {
        binding.tvErrorMessage.text = message
        binding.cardError.visibility = View.VISIBLE
        binding.cardResult.visibility = View.GONE
    }

    /**
     * Oculta o teclado virtual do dispositivo.
     */
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }
}
