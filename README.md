# how to run local
* make sure postgres is installed at the default port 5432 and running (windows services -> start)
* open pgAdmin and then create role = realworld
* create a DB called realworld
* run the below script
```sql
-- Schema: public

-- DROP SCHEMA public;

CREATE SCHEMA public
  AUTHORIZATION postgres;

GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO public;
GRANT ALL ON SCHEMA public TO realworld;


COMMENT ON SCHEMA public
  IS 'standard public schema';


CREATE TABLE users (
  id UUID NOT NULL,
  email TEXT NOT NULL,
  token TEXT NOT NULL,
  username TEXT NOT NULL,
  password TEXT NOT NULL,
  bio TEXT,
  image TEXT,

  CONSTRAINT pk$users PRIMARY KEY (id),
  CONSTRAINT unq$email UNIQUE (email),
  CONSTRAINT unq$username UNIQUE (username)
);

CREATE TABLE follows (
  followee UUID NOT NULL,
  follower UUID NOT NULL,

  CONSTRAINT pk$follows PRIMARY KEY (follower, followee),
  CONSTRAINT fk$follower FOREIGN KEY (follower) REFERENCES users ON DELETE CASCADE,
  CONSTRAINT fk$followee FOREIGN KEY (followee) REFERENCES users ON DELETE CASCADE
);

CREATE TABLE articles (
  id UUID NOT NULL,
  slug TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  body TEXT NOT NULL,
  author UUID NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT pk$articles PRIMARY KEY (id),
  CONSTRAINT unq$slug UNIQUE (slug),
  CONSTRAINT fk$author FOREIGN KEY (author) REFERENCES users ON DELETE CASCADE
);

CREATE TABLE tags (
  name TEXT NOT NULL,

  CONSTRAINT pk$tags PRIMARY KEY (name)
);

CREATE TABLE article_tags (
  article_id UUID NOT NULL,
  tag TEXT NOT NULL,

  CONSTRAINT pk$acticle_tags PRIMARY KEY (article_id, tag),
  CONSTRAINT fk$article_id FOREIGN KEY (article_id) REFERENCES articles ON DELETE CASCADE,
  CONSTRAINT fk$tag FOREIGN KEY (tag) REFERENCES tags ON DELETE CASCADE
);

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, TRIGGER, INSERT, UPDATE, DELETE ON TABLES TO realworld;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO realworld;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO realworld;


ALTER TABLE public.article_tags OWNER TO realworld;
ALTER TABLE public.articles OWNER TO realworld;
ALTER TABLE public.follows OWNER TO realworld;
ALTER TABLE public.tags OWNER TO realworld;
ALTER TABLE public.users OWNER TO realworld;




```

