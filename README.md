# Gungnir Playground

A repository with example code for the
[Gungnir](https://www.github.com/kwrooijen/gungnir) library. You can open the
`core` namespace in your text editor and evaluate the inline code step by step.

This playground is meant as a supplement to the official [Gungnir
guide](https://kwrooijen.github.io/gungnir/guide.html).

## Requirements

In order for this to work you need a running Postgres instance. This project
also provides a `docker-compose.yml` file which defines a Posgres
container. Simply run the following command to setup a Posgres instance
(assuming you have the `docker-compose` command installed).

```sh
docker-compose up -d
```

Alternatively you can setup Postgres however you like, as long as you configure
the `PORT` properly in the `core.clj` file.


## Author / License

Released under the [MIT License] by [Kevin William van Rooijen].

[Kevin William van Rooijen]: https://twitter.com/kwrooijen

[MIT License]: https://github.com/kwrooijen/gungnir-playground/blob/master/LICENSE
