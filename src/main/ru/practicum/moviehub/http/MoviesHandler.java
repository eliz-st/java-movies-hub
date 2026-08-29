package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {

    private final MoviesStore store;
    private final Gson gson = new Gson();

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();

        if (method.equalsIgnoreCase("GET")) {
            String path = ex.getRequestURI().getPath();

            if (path.equals("/movies")) {
                String query = ex.getRequestURI().getQuery();

                if (query != null && query.startsWith("year=")) {
                    String yearString = query.substring("year=".length());

                    int year;

                    try {
                        year = Integer.parseInt(yearString);
                    } catch (NumberFormatException e) {
                        ErrorResponse errorResponse = new ErrorResponse(
                                "Некорректный параметр запроса — 'year'",
                                List.of("Параметр year должен быть числом"));

                        String errorJson = gson.toJson(errorResponse);
                        sendJson(ex, 400, errorJson);
                        return;
                    }
                    List<Movie> filteredMovies = store.getByYear(year);
                    String json = gson.toJson(filteredMovies);
                    sendJson(ex, 200, json);
                    return;
                }

                if (query != null) {
                    ErrorResponse errorResponse = new ErrorResponse(
                            "Некорректный параметр запроса",
                            List.of("Поддерживается только параметр year"));

                    String errorJson = gson.toJson(errorResponse);
                    sendJson(ex, 400, errorJson);
                    return;
                }

                String json = gson.toJson(store.getAll());
                sendJson(ex, 200, json);

            } else {
                String idString = path.substring("/movies/".length());

                int id;

                try {
                    id = Integer.parseInt(idString);
                } catch (NumberFormatException e) {
                    ErrorResponse errorResponse = new ErrorResponse("Некорректный ID",
                            List.of("ID фильма должен быть числом"));

                    String errorJson = gson.toJson(errorResponse);
                    sendJson(ex, 400, errorJson);
                    return;
                }

                Movie movie = store.getById(id);

                if (movie == null) {
                    ErrorResponse errorResponse = new ErrorResponse("Фильм не найден",
                            List.of("Фильма с ID " + id + " не существует"));

                    String errorJson = gson.toJson(errorResponse);
                    sendJson(ex, 404, errorJson);
                    return;
                }

                String json = gson.toJson(movie);
                sendJson(ex, 200, json);
            }

        } else if (method.equalsIgnoreCase("POST")) {
            String path = ex.getRequestURI().getPath();

            if (!path.equals("/movies")) {
                sendJson(ex, 400,"{\"error\":\"Некорректный путь\"}");
                return;
            }

            handlePost(ex);

        } else if (method.equalsIgnoreCase("DELETE")) {
            handleDelete(ex);

        } else {
            sendJson(ex, 405, "{\"error\":\"Метод не поддерживается\"}");
        }
    }

    private void handleDelete(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        if (path.equals("/movies")) {
            ErrorResponse errorResponse = new ErrorResponse(
                    "Некорректный ID",
                    List.of("ID фильма не указан"));

            String errorJson = gson.toJson(errorResponse);
            sendJson(ex, 400, errorJson);
            return;
        }

        String idString = path.substring("/movies/".length());

        int id;

        try {
            id = Integer.parseInt(idString);
        } catch (NumberFormatException e) {
            ErrorResponse errorResponse = new ErrorResponse(
                    "Некорректный ID",
                    List.of("ID фильма должен быть числом"));

            String errorJson = gson.toJson(errorResponse);
            sendJson(ex, 400, errorJson);
            return;
        }

        Movie deletedMovie = store.delete(id);

        if (deletedMovie == null) {
            ErrorResponse errorResponse = new ErrorResponse("Фильм не найден",
                    List.of("Фильма с ID " + id + " не существует"));

            String errorJson = gson.toJson(errorResponse);
            sendJson(ex, 404, errorJson);
            return;
        }

        sendNoContent(ex);
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");

        if (contentType == null
                || !contentType.toLowerCase().startsWith("application/json")) {

            sendJson(ex, 415, "{\"error\":\"Неподдерживаемый Content-Type\"}");
            return;
        }

        String body = new String(ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        Movie movie;

        try {
            movie = gson.fromJson(body, Movie.class);
        } catch (JsonSyntaxException e) {
            sendJson(ex, 400, "{\"error\":\"Некорректный JSON\"}");
            return;
        }

        if (movie == null) {
            ErrorResponse errorResponse = new ErrorResponse("Ошибка валидации",
                    List.of("данные фильма не должны быть null"));

            String errorJson = gson.toJson(errorResponse);
            sendJson(ex, 422, errorJson);
            return;
        }

        List<String> validationErrors = new ArrayList<>();

        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            validationErrors.add("название не должно быть пустым");
        }

        if (movie.getTitle() != null
                && movie.getTitle().length() > 100) {

            validationErrors.add("название не должно быть длиннее 100 символов");
        }

        if (movie.getYear() < 1888) {
            validationErrors.add("год должен быть не меньше 1888");
        }

        if (movie.getYear() > Year.now().getValue() + 1) {
            validationErrors.add("год не должен быть больше следующего года");
        }

        if (!validationErrors.isEmpty()) {
            ErrorResponse errorResponse = new ErrorResponse("Ошибка валидации",
                    validationErrors);
            String errorJson = gson.toJson(errorResponse);
            sendJson(ex, 422, errorJson);
            return;
        }
        Movie createdMovie = store.add(movie);
        String json = gson.toJson(createdMovie);
        sendJson(ex, 201, json);
    }
}
