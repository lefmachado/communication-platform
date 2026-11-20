package br.com.lefmachado.communication;

import org.springframework.boot.SpringApplication;

public class TestCommunicationApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(CommunicationApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
