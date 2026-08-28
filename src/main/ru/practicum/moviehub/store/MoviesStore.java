package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MoviesStore {

    private final Map<Integer, Movie> movies = new HashMap<>();
    private int nextId = 1;

    public Movie add(Movie movie) {
        movie.setId(nextId);
        movies.put(nextId, movie);
        nextId++;
        return movie;
    }

    public Collection<Movie> getAll() {
        return movies.values();
    }

    public Movie getById(int id) {
        return movies.get(id);
    }

    public Movie delete(int id) {
        return movies.remove(id);
    }

    public void clear() {
        movies.clear();
        nextId = 1;
    }

}