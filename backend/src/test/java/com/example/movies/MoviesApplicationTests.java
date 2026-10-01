package com.example.movies;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Import disabled so running the tests doesn't add movies to the development database.
@SpringBootTest(properties = "movies.seed.enabled=false")
class MoviesApplicationTests {

	@Test
	void contextLoads() {
	}

}
