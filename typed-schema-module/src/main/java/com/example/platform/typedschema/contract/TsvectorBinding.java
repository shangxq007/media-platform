package com.example.platform.typedschema.contract;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;

import org.jooq.Binding;
import org.jooq.BindingGetResultSetContext;
import org.jooq.BindingGetSQLInputContext;
import org.jooq.BindingGetStatementContext;
import org.jooq.BindingRegisterContext;
import org.jooq.BindingSQLContext;
import org.jooq.BindingSetSQLOutputContext;
import org.jooq.BindingSetStatementContext;
import org.jooq.Converter;
import org.jooq.impl.DSL;

/**
 * Custom jOOQ binding for PostgreSQL {@code tsvector} columns.
 *
 * <p>PostgreSQL's {@code tsvector} type has no direct JDBC mapping.
 * This binding reads and writes {@code tsvector} values as opaque
 * SQL strings, preserving the native PostgreSQL representation.</p>
 *
 * <p>The Java type is {@link TsvectorValue}, a value wrapper that
 * holds the raw tsvector text. This prevents accidental use of
 * {@code Object} (the jOOQ default for unknown types) and makes
 * tsvector columns type-safe at the Java level.</p>
 *
 * <p>Null safety: {@code null} SQL values produce {@code null} Java values
 * and vice versa.</p>
 */
public final class TsvectorBinding implements Binding<Object, TsvectorValue> {

    private static final long serialVersionUID = 1L;

    private static final Converter<Object, TsvectorValue> CONVERTER = new Converter<>() {
        @Override
        public TsvectorValue from(Object databaseObject) {
            if (databaseObject == null) {
                return null;
            }
            return new TsvectorValue(databaseObject.toString());
        }

        @Override
        public Object to(TsvectorValue userObject) {
            if (userObject == null) {
                return null;
            }
            return userObject.value();
        }

        @Override
        public Class<Object> fromType() {
            return Object.class;
        }

        @Override
        public Class<TsvectorValue> toType() {
            return TsvectorValue.class;
        }
    };

    @Override
    public Converter<Object, TsvectorValue> converter() {
        return CONVERTER;
    }

    @Override
    public void sql(BindingSQLContext<TsvectorValue> ctx) throws SQLException {
        if (ctx.value() == null) {
            ctx.render().visit(DSL.sql("null"));
        } else {
            ctx.render().visit(DSL.sql("?::tsvector"));
        }
    }

    @Override
    public void register(BindingRegisterContext<TsvectorValue> ctx) throws SQLException {
        ctx.statement().registerOutParameter(ctx.index(), Types.VARCHAR);
    }

    @Override
    public void set(BindingSetStatementContext<TsvectorValue> ctx) throws SQLException {
        TsvectorValue value = ctx.value();
        if (value == null) {
            ctx.statement().setNull(ctx.index(), Types.VARCHAR);
        } else {
            ctx.statement().setString(ctx.index(), value.value());
        }
    }

    @Override
    public void set(BindingSetSQLOutputContext<TsvectorValue> ctx) throws SQLException {
        throw new SQLFeatureNotSupportedException("tsvector binding does not support SQLData");
    }

    @Override
    public void get(BindingGetResultSetContext<TsvectorValue> ctx) throws SQLException {
        String raw = ctx.resultSet().getString(ctx.index());
        ctx.value(raw == null ? null : new TsvectorValue(raw));
    }

    @Override
    public void get(BindingGetStatementContext<TsvectorValue> ctx) throws SQLException {
        String raw = ctx.statement().getString(ctx.index());
        ctx.value(raw == null ? null : new TsvectorValue(raw));
    }

    @Override
    public void get(BindingGetSQLInputContext<TsvectorValue> ctx) throws SQLException {
        throw new SQLFeatureNotSupportedException("tsvector binding does not support SQLData");
    }
}
