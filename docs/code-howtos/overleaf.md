# Notes on the overleaf implementation

Local setup of overleaf:

1. Download <https://github.com/overleaf/overleaf/blob/master/docker-compose.yml>.
2. Adapt `volumes` (<https://github.com/overleaf/overleaf/blob/245fc120e6d50e8ee02d5900b8d6a8d6d8b9135c/docker-compose.yml#L20>).
3. Execute `docker compose up`.
4. Open <http://localhost/launchpad> to create the administrator user.
5. Create the test user <jabref@example.org>.
6. Copy the displayed password reset URL
7. Open your browser in a private session.
8. Go to the URL copied in step 6 and set the password `jabref`.
9. Create a latex project with the example.

After a successful setup, one can use <http://localhost> as "Server Address".

After a session execute `docker compose down`

More information on Overleaf is available at <https://github.com/overleaf/overleaf/wiki/Quick-Start-Guide>.

