package ru.practicum.moviehub.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import ru.practicum.moviehub.model.Movie;
import com.google.gson.Gson;
import java.util.List;
import java.time.Year;
import ru.practicum.moviehub.api.ErrorResponse;

public class MoviesApiTest {

    private static final String BASE = "http://localhost:8080";
    private static MoviesServer server;
    private static MoviesStore store;
    private static HttpClient client;
    private static final Gson gson = new Gson();

    @BeforeAll
    static void beforeAll() {
        store = new MoviesStore();
        server = new MoviesServer(store, 8080);
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        store.clear();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode(),
                "GET /movies должен вернуть 200");

        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");

        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");

        assertEquals("[]",resp.body().trim(), 
                "При пустом хранилище должен возвращаться пустой JSON-массив");

    }

    @Test
    void getMovies_whenMovieExists_returnsMovie() throws Exception {
        Movie movie = new Movie("Интерстеллар", 2014);
        store.add(movie);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        List<Movie> movies = gson.fromJson(resp.body(),
                new ListOfMoviesTypeToken().getType());

        assertEquals(200, resp.statusCode(),
                "GET /movies должен вернуть 200");

        assertEquals(1, movies.size(),
                "Должен вернуться один фильм");

        Movie returnedMovie = movies.get(0);

        assertEquals("Интерстеллар", returnedMovie.getTitle(),
                "Название фильма должно совпадать");

        assertEquals(2014, returnedMovie.getYear(),
                "Год фильма должен совпадать");

        assertEquals(movie.getId(), returnedMovie.getId(),
                "ID фильма должен совпадать");
    }

    @Test
    void postMovies_withValidMovie_addsMovie() throws Exception {
        Movie movie = new Movie("Матрица", 1999);
        String json = gson.toJson(movie);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, resp.statusCode(),
                "POST /movies должен вернуть 201");

        Movie createdMovie = gson.fromJson(resp.body(), Movie.class);

        assertEquals("Матрица", createdMovie.getTitle(),
                "Название фильма должно совпадать");

        assertEquals(1999, createdMovie.getYear(),
                "Год фильма должен совпадать");

        assertEquals(1, createdMovie.getId(),
                "Сервер должен присвоить фильму ID");
    }

    @Test
    void postMovies_withEmptyTitle_returns422() throws Exception {
        Movie movie = new Movie("", 1999);
        String json = gson.toJson(movie);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(),
                "При пустом title сервер должен вернуть 422");
        ErrorResponse errorResponse = gson.fromJson(
                resp.body(),
                ErrorResponse.class);

        assertEquals("Ошибка валидации", errorResponse.getError(),
                "В ответе должна быть ошибка валидации");
    }

    @Test
    void postMovies_withTooLongTitle_returns422() throws Exception {
        String longTitle = "А".repeat(101);
        Movie movie = new Movie(longTitle, 1999);
        String json = gson.toJson(movie);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(),
                "При title длиннее 100 символов сервер должен вернуть 422");

    }

    @Test
    void postMovies_withTooEarlyYear_returns422() throws Exception {
        Movie movie = new Movie("Слишком старый фильм", 1887);
        String json = gson.toJson(movie);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(),
                "При year меньше 1888 сервер должен вернуть 422");
    }

    @Test
    void postMovies_withTooLateYear_returns422() throws Exception {
        int invalidYear = Year.now().getValue() + 2;
        Movie movie = new Movie("Фильм из будущего", invalidYear);
        String json = gson.toJson(movie);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(),
                "При слишком большом year сервер должен вернуть 422");
    }

    @Test
    void postMovies_withWrongContentType_returns415() throws Exception {
        Movie movie = new Movie("Матрица", 1999);
        String json = gson.toJson(movie);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode(),
                "При неправильном Content-Type сервер должен вернуть 415");
    }

    @Test
    void postMovies_withInvalidJson_returns400() throws Exception {
        String invalidJson = "{ \"title\": \"Матрица\", ";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(),
                "При некорректном JSON сервер должен вернуть 400");
    }

    @Test
    void getMovieById_whenMovieExists_returnsMovie() throws Exception {
        Movie movie = new Movie("Интерстеллар", 2014);
        store.add(movie);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + movie.getId()))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(),
                "GET /movies/{id} должен вернуть 200");

        Movie returnedMovie = gson.fromJson(
                resp.body(),
                Movie.class);

        assertEquals(movie.getId(), returnedMovie.getId(),
                "ID фильма должен совпадать");

        assertEquals("Интерстеллар", returnedMovie.getTitle(),
                "Название фильма должно совпадать");

        assertEquals(2014, returnedMovie.getYear(),
                "Год фильма должен совпадать");
    }

    @Test
    void getMovieById_whenMovieDoesNotExist_returns404() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode(),
                "Если фильма с таким ID нет, сервер должен вернуть 404");
    }

    @Test
    void getMovieById_whenIdIsNotNumber_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(),
                "Если ID не число, сервер должен вернуть 400");
    }

    @Test
    void deleteMovie_whenMovieExists_returns204() throws Exception {
        Movie movie = new Movie("Матрица", 1999);
        store.add(movie);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/" + movie.getId()))
                .DELETE()
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(204, resp.statusCode(),
                "DELETE /movies/{id} должен вернуть 204");
    }

    @Test
    void deleteMovie_whenMovieDoesNotExist_returns404() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .DELETE()
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, resp.statusCode(),
                "Если фильма с таким ID нет, DELETE должен вернуть 404");
    }

    @Test
    void deleteMovie_whenIdIsNotNumber_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/abc"))
                .DELETE()
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(),
                "Если ID не число, DELETE должен вернуть 400");
    }

    @Test
    void getMoviesByYear_returnsMoviesOfSpecifiedYear() throws Exception {
        store.add(new Movie("Матрица", 1999));
        store.add(new Movie("Интерстеллар", 2014));
        store.add(new Movie("Начало", 2010));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2014"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(),
                "GET /movies?year=2014 должен вернуть 200");

        List<Movie> movies = gson.fromJson(
                resp.body(),
                new ListOfMoviesTypeToken().getType());

        assertEquals(1, movies.size(),
                "Должен вернуться один фильм");

        assertEquals("Интерстеллар", movies.get(0).getTitle(),
                "Должен вернуться фильм указанного года");

        assertEquals(2014, movies.get(0).getYear(),
                "Год фильма должен быть 2014");
    }

    @Test
    void getMoviesByYear_whenNoMoviesFound_returnsEmptyList() throws Exception {
        store.add(new Movie("Матрица", 1999));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=2014"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(),
                "GET /movies?year=2014 должен вернуть 200");

        assertEquals("[]", resp.body().trim(),
                "Если фильмов указанного года нет, должен вернуться пустой список");
    }

    @Test
    void getMoviesByYear_whenYearIsNotNumber_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?year=abc"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(),
                "Если параметр year не число, сервер должен вернуть 400");
    }

    @Test
    void unsupportedHttpMethod_returns405() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(405, resp.statusCode(),
                "Неподдерживаемый HTTP-метод должен вернуть 405");
    }

    @Test
    void getMovieById_whenMovieDoesNotExist_returnsErrorResponse() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/999"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse errorResponse = gson.fromJson(
                resp.body(),
                ErrorResponse.class);

        assertEquals(404, resp.statusCode(),
                "Если фильма нет, сервер должен вернуть 404");

        assertEquals("Фильм не найден", errorResponse.getError(),
                "В ответе должна быть ошибка «Фильм не найден»");
    }

    @Test
    void postMovies_withEmptyTitle_returnsValidationDetails() throws Exception {
        Movie movie = new Movie("", 1999);
        String json = gson.toJson(movie);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse errorResponse = gson.fromJson(
                resp.body(),
                ErrorResponse.class);

        assertEquals(422, resp.statusCode(),
                "При пустом названии сервер должен вернуть 422");

        assertEquals("Ошибка валидации", errorResponse.getError(),
                "Должна вернуться ошибка валидации");

        assertEquals(
                List.of("название не должно быть пустым"),
                errorResponse.getDetails(),
                "В details должна быть указана конкретная причина ошибки");
    }

    @Test
    void postMovies_withTooLongTitle_returnsValidationDetails() throws Exception {
        String longTitle = "А".repeat(101);
        Movie movie = new Movie(longTitle, 1999);
        String json = gson.toJson(movie);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse errorResponse = gson.fromJson(
                resp.body(),
                ErrorResponse.class);

        assertEquals(422, resp.statusCode(),
                "При слишком длинном названии сервер должен вернуть 422");

        assertEquals(
                List.of("название не должно быть длиннее 100 символов"),
                errorResponse.getDetails(),
                "В details должна быть указана конкретная причина ошибки");
    }

    @Test
    void postMovies_withTooEarlyYear_returnsValidationDetails() throws Exception {
        Movie movie = new Movie("Слишком старый фильм", 1887);
        String json = gson.toJson(movie);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse errorResponse = gson.fromJson(
                resp.body(),
                ErrorResponse.class);

        assertEquals(422, resp.statusCode(),
                "При слишком раннем годе сервер должен вернуть 422");

        assertEquals(
                List.of("год должен быть не меньше 1888"),
                errorResponse.getDetails(),
                "В details должна быть указана конкретная причина ошибки");
    }

    @Test
    void postMovies_withTooLateYear_returnsValidationDetails() throws Exception {
        int invalidYear = Year.now().getValue() + 2;
        Movie movie = new Movie("Фильм из будущего", invalidYear);
        String json = gson.toJson(movie);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse errorResponse = gson.fromJson(
                resp.body(),
                ErrorResponse.class);

        assertEquals(422, resp.statusCode(),
                "При слишком большом годе сервер должен вернуть 422");

        assertEquals(
                List.of("год не должен быть больше следующего года"),
                errorResponse.getDetails(),
                "В details должна быть указана конкретная причина ошибки");
    }

    @Test
    void postMovies_withSeveralValidationErrors_returnsAllDetails() throws Exception {
        Movie movie = new Movie("", 1887);
        String json = gson.toJson(movie);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ErrorResponse errorResponse = gson.fromJson(
                resp.body(),
                ErrorResponse.class);

        assertEquals(422, resp.statusCode(),
                "При ошибках валидации сервер должен вернуть 422");

        assertEquals(
                List.of("название не должно быть пустым",
                        "год должен быть не меньше 1888"),
                errorResponse.getDetails(),
                "В details должны быть указаны все ошибки");
    }

    @Test
    void postMovies_withJsonAndCharsetContentType_returns201() throws Exception {
        Movie movie = new Movie("Матрица", 1999);
        String json = gson.toJson(movie);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(201, resp.statusCode(),
                "Content-Type application/json с кодировкой должен приниматься");
    }
    @Test
    void postMovies_withNullJson_returns422() throws Exception {
        String json = "null";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(422, resp.statusCode(),
                "Если вместо фильма передан null, сервер должен вернуть 422");
    }

    @Test
    void getMovies_withUnknownQueryParameter_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies?name=matrix"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(),
                "При неизвестном параметре запроса сервер должен вернуть 400");
    }

    @Test
    void deleteMovie_withoutId_returns400() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .DELETE()
                .build();
        HttpResponse<String> resp = client.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(),
                "DELETE /movies без ID должен вернуть 400");
    }

    @Test
    void postMovies_withIdInPath_returns400() throws Exception {
        Movie movie = new Movie("Матрица", 1999);
        String json = gson.toJson(movie);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies/123"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        json,
                        StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(400, resp.statusCode(),
                "POST должен выполняться только для /movies");
    }



}