# Nautilus

Nautilus is a user authentication and management service.

## Work In Progress
Please note, this is an on-going, incomplete project! While the core
functionality should remain stable, some important features are not yet
implemented and design is subject to change.

## Setup
Currently Nautilus requires Riak 1.4 as its persistence layer. Ensure Riak is
installed and running. By default, Nautilus looks for Riak on `localhost:8087`.

## Usage
Nautilus is a standalone service which provides pre-authenticated, transient
proxies to privileged backend services. For instance, a client application can
use Nautilus to obtain a portal which maps to a user's profile page. Because
portals are pre-authenticated, there is no need for an application to check
credentials against the database on every request. After a set period of time,
a portal expires and consuming applications may respond by transparently
requesting a new portal.

A complete flow, from user creation through portal creation works like this:

1. A user is created via the user creation endpoint
2. An OAuth 2.0 Bearer Token is created on behalf of the user
3. A request for a portal against an existing backend service is made
4. Using the portal ID obtained in step 3, a proxied request is made

To clarify how this might work with a real client application, let's walk
through the above flow using curl. (Assume we have an instance of Nautilus
running on localhost port 3000.

```sh
$ curl -X POST http://localhost:3000/user \
       -d '{"email": "foo@bar.tld", "password": "hunter2"}' \
       -H 'Content-Type: application/json'

{}
```

This should yield an HTTP 201 Created response. Using these credentials we can
request a new Bearer Token for this user. Note that we will also need to
provide application credentials as Basic Auth.

```sh
$ curl -X POST http://localhost:3000/token \
       -d "username=foo%40bar.tld&password=hunter2&grant_type=password" \
       -u foo:bar

{
  "token_type": "Bearer",
  "access_token": "q0VKvZ5xll3mmdsYcC1L57hd",
  "user": {
    "login": "foo@bar.tld"
  }
}
```

Nautilus will respond by issuing a new token to for the provided username. Bear
in mind this implies any previous token is no longer valid. Using this token,
a portal can be requested.

```sh
$ curl -X POST http://localhost:3000/service/endpoint \
       -H 'Authorization: Bearer q0VKvZ5xll3mmdsYcC1L57hd'

{
  "uri": "http://localhost:3000/portal",
  "ttl": 1410617001663,
  "headers": {
    "X-Portal-Id": "cdc64532-8687-4396-aa36-49bc6482bdc4"
  }
}
```

The above requests a portal for the service named "service" and its endpoint
"endpoint". Services may be registered with Nautilus via a separate endpoint
and may point to any HTTP backend service.

Using the URI in conjunction with the headers above, we can now request the
authenticated resource.

```sh
$ curl http://localhost:3000/portal
       -H 'X-Portal-Id: cdc64532-8687-4396-aa36-49bc6482bdc4'
```

This is the basic authentication flow Nautilus provides. Additional endpoints
are provided to allow for adding, updating, and removing services and users.

## Endpoints
Several endpoints facilitate user management, backend service management, and
portal lifecycles.

### User Creation

**request**
```http
POST /user HTTP/1.1
Host: localhost:3000
Content-Type: application/json

{
    "email": "foo@bar.tld",
    "password": "hunter2"
}
```

**response**
```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=utf-8
X-Request-Id: 2e8d4129-74f0-4d55-824c-a4609624ae81
```

### Bearer Token Creation

**request**
```http
POST /token HTTP/1.1
Authorization: Basic Zm9vOmJhcg==
Host: localhost:3000
Content-Type: application/x-www-form-urlencoded

"username=foo%40bar.tld&password=hunter2&grant_type=password"
```

**response**
```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=utf-8
X-Request-Id: ed9d091f-e289-4fbc-8057-bde5c1a02f99

{
    "token_type": "Bearer",
    "access_token": "L0rF0wJTxyHk2K3zzpD4hHrv",
    "user": {
        "login": "foo@bar.tld"
    }
}
```

### Portal Creation

**request**
```http
POST /service/endpoint HTTP/1.1
Host: localhost:3000
Authorization: Bearer L0rF0wJTxyHk2K3zzpD4hHrv
```

**response**
```http
HTTP/1.1 201 Created
Content-Type: application/json; charset=utf-8
X-Request-Id: de66983f-51ee-4c9c-a38e-0389fc86eabb

{
    "uri": "http://localhost:3000/portal",
    "ttl": 1409594011758,
    "headers": {
        "X-Portal-Id": "26379be2-f1e9-4710-b599-e56e0366d700"
    }
}
```
