var e=e=>{switch(e){case`index`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=index,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        label="\\N",
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    user [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">User</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Platform user</FONT></TD></TR></TABLE>>,
        likec4_id=user,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    mediaplatform [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">media-platform</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Render platform</FONT></TD></TR></TABLE>>,
        likec4_id=mediaPlatform,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    user -> mediaplatform [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">uses</FONT></TD></TR></TABLE>>,
        likec4_id="77kvz4",
        minlen=1,
        style=dashed];
    reviewer [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Human Reviewer</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Final arbiter</FONT></TD></TR></TABLE>>,
        likec4_id=reviewer,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    hermes [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Hermes Control Plane</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Multi-agent orchestration</FONT></TD></TR></TABLE>>,
        likec4_id=hermes,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    reviewer -> hermes [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">reviews</FONT></TD></TR></TABLE>>,
        likec4_id="1puz3yb",
        minlen=1,
        style=dashed];
    cloudflare [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Cloudflare</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">R2, Pages, AI Gateway</FONT></TD></TR></TABLE>>,
        likec4_id=cloudflare,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    hermes -> cloudflare [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">publishes</FONT></TD></TR></TABLE>>,
        likec4_id=swbi77,
        minlen=1,
        style=dashed];
    telegram [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Telegram</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Notifications</FONT></TD></TR></TABLE>>,
        likec4_id=telegram,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    hermes -> telegram [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">notifies</FONT></TD></TR></TABLE>>,
        likec4_id="47if73",
        minlen=1,
        style=dashed];
    hermes -> mediaplatform [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">develops</FONT></TD></TR></TABLE>>,
        likec4_id="17mj72t",
        style=dashed];
    aiproviders [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">AI Providers</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">MiMo, LongCat, OpenRouter, etc.</FONT></TD></TR></TABLE>>,
        likec4_id=aiProviders,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    mediaplatform -> aiproviders [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">calls models</FONT></TD></TR></TABLE>>,
        likec4_id="1kprbf",
        minlen=1,
        style=dashed];
    storage [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Object storage / shared filesystem</FONT></TD></TR></TABLE>>,
        likec4_id=storage,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    mediaplatform -> storage [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">reads/writes</FONT></TD></TR></TABLE>>,
        likec4_id="13h0zjc",
        minlen=1,
        style=dashed];
}
`;case`systemContext`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=systemContext,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        label="\\N",
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    subgraph cluster_mediaplatform {
        graph [color="#1b3d88",
            fillcolor="#194b9e",
            label=<<FONT POINT-SIZE="11" COLOR="#bfdbfeb3"><B>MEDIA-PLATFORM</B></FONT>>,
            likec4_depth=1,
            likec4_id=mediaPlatform,
            likec4_level=0,
            margin=40,
            style=filled
        ];
        opencue [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">OpenCue</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">ExecutionEnvironment only — NOT a Provider</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.opencue",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        remotion [height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Remotion</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Non-production/POC subtitle template provider</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.remotion",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
    }
    opencue -> remotion [style=invis];
}
`;case`containerDiagram`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=containerDiagram,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    subgraph cluster_mediaplatform {
        graph [color="#1b3d88",
            fillcolor="#194b9e",
            label=<<FONT POINT-SIZE="11" COLOR="#bfdbfeb3"><B>MEDIA-PLATFORM</B></FONT>>,
            likec4_depth=1,
            likec4_id=mediaPlatform,
            likec4_level=0,
            margin=40,
            style=filled
        ];
        platformapp [group=mediaPlatform,
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">platform-app</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Spring Boot entry point</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.platformApp",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        rendermodule [group=mediaPlatform,
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">render-module</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Core render domain</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        aimodule [group=mediaPlatform,
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">ai-module</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">AI integration</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.aiModule",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        opencue [group=mediaPlatform,
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">OpenCue</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">ExecutionEnvironment only — NOT a Provider</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.opencue",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        remotion [group=mediaPlatform,
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Remotion</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Non-production/POC subtitle template provider</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.remotion",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        sharedkernel [group=mediaPlatform,
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">shared-kernel</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Shared domain primitives</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.sharedKernel",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
    }
    platformapp -> rendermodule [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">uses</FONT></TD></TR></TABLE>>,
        likec4_id=ap697o,
        style=dashed,
        weight=2];
    platformapp -> aimodule [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">uses</FONT></TD></TR></TABLE>>,
        likec4_id=oyorty,
        style=dashed];
    rendermodule -> opencue [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">routes ExecutionEnv only</FONT></TD></TR></TABLE>>,
        likec4_id="16zyo0t",
        minlen=1,
        style=dashed,
        weight=2];
    rendermodule -> remotion [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">routes POC only</FONT></TD></TR></TABLE>>,
        likec4_id="1rv9fdf",
        minlen=1,
        style=dashed,
        weight=2];
    storage [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Object storage / shared filesystem</FONT></TD></TR></TABLE>>,
        likec4_id=storage,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    rendermodule -> storage [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">persists artifacts</FONT></TD></TR></TABLE>>,
        likec4_id="99aifq",
        minlen=1,
        style=dashed];
    rendermodule -> sharedkernel [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">depends on</FONT></TD></TR></TABLE>>,
        likec4_id="86h9qg",
        style=dashed,
        weight=2];
    aimodule -> sharedkernel [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">depends on</FONT></TD></TR></TABLE>>,
        likec4_id="18eum16",
        style=dashed];
}
`;case`controlPlane`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=controlPlane,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    subgraph cluster_hermes {
        graph [color="#1b3d88",
            fillcolor="#194b9e",
            label=<<FONT POINT-SIZE="11" COLOR="#bfdbfeb3"><B>HERMES CONTROL PLANE</B></FONT>>,
            likec4_depth=1,
            likec4_id=hermes,
            likec4_level=0,
            margin=40,
            style=filled
        ];
        {
            graph [rank=same];
            hermesagent [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Hermes Agent</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Gateway, orchestrator, Level 3 Feature<BR/>Coordinator</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.hermesAgent",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
            codingagents [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Coding Agents</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">OpenCode, Codex, Kilo Code, Claude Code,<BR/>Aider</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.codingAgents",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
            reviewinfra [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Review Infrastructure</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">CODEOWNERS, Semgrep, Review Packets</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.reviewInfra",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
        }
        {
            graph [rank=same];
            policies [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Policies</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Permissions, stop conditions</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.policies",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
            dashboard [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Dashboard</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">scribe.cc.cd</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.dashboard",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
        }
        hermesagent -> policies [style=invis];
    }
    codingagents -> reviewinfra [style=invis];
    reviewinfra -> dashboard [style=invis];
}
`;case`controlPlaneContext`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=controlPlaneContext,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    subgraph cluster_hermes {
        graph [color="#1b3d88",
            fillcolor="#194b9e",
            label=<<FONT POINT-SIZE="11" COLOR="#bfdbfeb3"><B>HERMES CONTROL PLANE</B></FONT>>,
            likec4_depth=1,
            likec4_id=hermes,
            likec4_level=0,
            margin=40,
            style=filled
        ];
        {
            graph [rank=same];
            hermesagent [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Hermes Agent</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Gateway, orchestrator, Level 3 Feature<BR/>Coordinator</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.hermesAgent",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
            codingagents [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Coding Agents</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">OpenCode, Codex, Kilo Code, Claude Code,<BR/>Aider</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.codingAgents",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
            reviewinfra [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Review Infrastructure</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">CODEOWNERS, Semgrep, Review Packets</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.reviewInfra",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
        }
        {
            graph [rank=same];
            policies [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Policies</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Permissions, stop conditions</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.policies",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
            dashboard [height=2.5,
                label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Dashboard</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">scribe.cc.cd</FONT></TD></TR></TABLE>>,
                likec4_id="hermes.dashboard",
                likec4_level=1,
                margin="0.223,0.223",
                width=4.445];
        }
        hermesagent -> policies [minlen=1,
            style=invis];
    }
    codingagents -> reviewinfra [style=invis];
    cloudflare [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Cloudflare</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">R2, Pages, AI Gateway</FONT></TD></TR></TABLE>>,
        likec4_id=cloudflare,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    dashboard -> cloudflare [arrowhead=normal,
        likec4_id=swbi77,
        ltail=cluster_hermes,
        minlen=1,
        style=dashed,
        xlabel=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">publishes</FONT></TD></TR></TABLE>>];
    telegram [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Telegram</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Notifications</FONT></TD></TR></TABLE>>,
        likec4_id=telegram,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    dashboard -> telegram [arrowhead=normal,
        likec4_id="47if73",
        ltail=cluster_hermes,
        minlen=1,
        style=dashed,
        xlabel=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">notifies</FONT></TD></TR></TABLE>>];
    mediaplatform [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">media-platform</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Render platform</FONT></TD></TR></TABLE>>,
        likec4_id=mediaPlatform,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    dashboard -> mediaplatform [arrowhead=normal,
        likec4_id="17mj72t",
        ltail=cluster_hermes,
        style=dashed,
        xlabel=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">develops</FONT></TD></TR></TABLE>>];
    user [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">User</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Platform user</FONT></TD></TR></TABLE>>,
        likec4_id=user,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    user -> mediaplatform [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">uses</FONT></TD></TR></TABLE>>,
        likec4_id="77kvz4",
        minlen=0,
        style=dashed];
    reviewer [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Human Reviewer</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Final arbiter</FONT></TD></TR></TABLE>>,
        likec4_id=reviewer,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    reviewer -> hermesagent [arrowhead=normal,
        lhead=cluster_hermes,
        likec4_id="1puz3yb",
        minlen=1,
        style=dashed,
        xlabel=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">reviews</FONT></TD></TR></TABLE>>];
}
`;case`vs0VerticalSlice`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=vs0VerticalSlice,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    subgraph cluster_rendermodule {
        graph [color="#1b3d88",
            fillcolor="#194b9e",
            label=<<FONT POINT-SIZE="11" COLOR="#bfdbfeb3"><B>RENDER-MODULE</B></FONT>>,
            likec4_depth=1,
            likec4_id="mediaPlatform.renderModule",
            likec4_level=0,
            margin=40,
            style=filled
        ];
        timelineedit [group="mediaPlatform.renderModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Timeline Edit Command</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">TL.0: Sealed interface with 12 typed command<BR/>records</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.timelineEdit",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        captiontemplate [group="mediaPlatform.renderModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Caption Template</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">CT.0: Typed intent model for caption/subtitle<BR/>rendering</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.captionTemplate",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        assstylemapper [group="mediaPlatform.renderModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">AssStyleMapper</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">CT.0: Maps CaptionTemplateSpec to ASS format<BR/>parameters</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.assStyleMapper",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        providerbinding [group="mediaPlatform.renderModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Provider Binding</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Deterministic eligibility + priority provider<BR/>selection</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.providerBinding",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        ffmpegbaseline [group="mediaPlatform.renderModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">FFmpeg/libass Baseline</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Production rendering baseline</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.ffmpegBaseline",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        productruntime [group="mediaPlatform.renderModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Product Runtime</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Product lifecycle management</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.productRuntime",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
        storageruntime [group="mediaPlatform.renderModule",
            height=2.5,
            label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage Runtime</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Storage/materialization management</FONT></TD></TR></TABLE>>,
            likec4_id="mediaPlatform.renderModule.storageRuntime",
            likec4_level=1,
            margin="0.223,0.223",
            width=4.445];
    }
    timelineedit -> captiontemplate [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">generates typed intent</FONT></TD></TR></TABLE>>,
        likec4_id="1e9y3o3",
        minlen=1,
        style=dashed];
    captiontemplate -> assstylemapper [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">maps to ASS parameters</FONT></TD></TR></TABLE>>,
        likec4_id="1ojuwrd",
        style=dashed];
    assstylemapper -> providerbinding [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">resolves provider</FONT></TD></TR></TABLE>>,
        likec4_id=dyb7bb,
        style=dashed];
    providerbinding -> ffmpegbaseline [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">routes PRODUCTION</FONT></TD></TR></TABLE>>,
        likec4_id=q47oy6,
        style=dashed];
    ffmpegbaseline -> productruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">produces output</FONT></TD></TR></TABLE>>,
        likec4_id="4joqk9",
        style=dashed];
    productruntime -> storageruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">manages lifecycle</FONT></TD></TR></TABLE>>,
        likec4_id="1h6i8bc",
        style=dashed,
        weight=3];
    storage [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Object storage / shared filesystem</FONT></TD></TR></TABLE>>,
        likec4_id=storage,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    storageruntime -> storage [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">persists artifacts</FONT></TD></TR></TABLE>>,
        likec4_id="1c6zqd9",
        minlen=1,
        style=dashed];
}
`;case`captionTemplateBoundary`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=captionTemplateBoundary,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    timelineedit [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Timeline Edit Command</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">TL.0: Sealed interface with 12 typed command<BR/>records</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.timelineEdit",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    captiontemplate [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Caption Template</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">CT.0: Typed intent model for caption/subtitle<BR/>rendering</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.captionTemplate",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    timelineedit -> captiontemplate [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">generates typed intent</FONT></TD></TR></TABLE>>,
        likec4_id="1e9y3o3",
        minlen=1,
        style=dashed];
    assstylemapper [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">AssStyleMapper</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">CT.0: Maps CaptionTemplateSpec to ASS format<BR/>parameters</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.assStyleMapper",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    captiontemplate -> assstylemapper [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">maps to ASS parameters</FONT></TD></TR></TABLE>>,
        likec4_id="1ojuwrd",
        style=dashed];
    providerbinding [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Provider Binding</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Deterministic eligibility + priority provider<BR/>selection</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.providerBinding",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    assstylemapper -> providerbinding [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">resolves provider</FONT></TD></TR></TABLE>>,
        likec4_id=dyb7bb,
        style=dashed];
    ffmpegbaseline [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">FFmpeg/libass Baseline</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Production rendering baseline</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.ffmpegBaseline",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    providerbinding -> ffmpegbaseline [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">routes PRODUCTION</FONT></TD></TR></TABLE>>,
        likec4_id=q47oy6,
        minlen=1,
        style=dashed];
}
`;case`productStorageBoundary`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=productStorageBoundary,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    ffmpegbaseline [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">FFmpeg/libass Baseline</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Production rendering baseline</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.ffmpegBaseline",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    productruntime [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Product Runtime</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Product lifecycle management</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.productRuntime",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    ffmpegbaseline -> productruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">produces output</FONT></TD></TR></TABLE>>,
        likec4_id="4joqk9",
        minlen=1,
        style=dashed];
    storageruntime [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage Runtime</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Storage/materialization management</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.storageRuntime",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    productruntime -> storageruntime [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">manages lifecycle</FONT></TD></TR></TABLE>>,
        likec4_id="1h6i8bc",
        style=dashed,
        weight=3];
    storage [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Storage</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Object storage / shared filesystem</FONT></TD></TR></TABLE>>,
        likec4_id=storage,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    storageruntime -> storage [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">persists artifacts</FONT></TD></TR></TABLE>>,
        likec4_id="1c6zqd9",
        minlen=1,
        style=dashed];
}
`;case`providerExecutionBoundary`:return`digraph {
    graph [TBbalance=min,
        bgcolor=transparent,
        compound=true,
        fontname=Arial,
        fontsize=20,
        labeljust=l,
        labelloc=t,
        layout=dot,
        likec4_viewId=providerExecutionBoundary,
        nodesep=1.528,
        outputorder=nodesfirst,
        pad=0.209,
        rankdir=TB,
        ranksep=1.667,
        splines=spline
    ];
    node [color="#2563eb",
        fillcolor="#3b82f6",
        fontcolor="#eff6ff",
        fontname=Arial,
        penwidth=0,
        shape=rect,
        style=filled
    ];
    edge [arrowsize=0.75,
        color="#8D8D8D",
        fontcolor="#C9C9C9",
        fontname=Arial,
        fontsize=14,
        penwidth=2,
        style=""
    ];
    providerbinding [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Provider Binding</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Deterministic eligibility + priority provider<BR/>selection</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.providerBinding",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    ffmpegbaseline [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">FFmpeg/libass Baseline</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Production rendering baseline</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.renderModule.ffmpegBaseline",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    providerbinding -> ffmpegbaseline [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">routes PRODUCTION</FONT></TD></TR></TABLE>>,
        likec4_id=q47oy6,
        minlen=1,
        style=dashed,
        weight=2];
    opencue [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">OpenCue</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">ExecutionEnvironment only — NOT a Provider</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.opencue",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    providerbinding -> opencue [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">routes ExecutionEnv only</FONT></TD></TR></TABLE>>,
        likec4_id="1yjkeoj",
        minlen=1,
        style=dashed];
    remotion [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">Remotion</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Non-production/POC subtitle template provider</FONT></TD></TR></TABLE>>,
        likec4_id="mediaPlatform.remotion",
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
    providerbinding -> remotion [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">routes POC only</FONT></TD></TR></TABLE>>,
        likec4_id="3xlixp",
        minlen=1,
        style=dashed];
}
`;default:throw Error(`Unknown viewId: `+e)}},t=e=>{switch(e){case`index`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="1425pt" height="1178pt"
 viewBox="0.00 0.00 1425.00 1178.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 1163.45)">
<!-- user -->
<g id="node1" class="node">
<title>user</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="535.04,-1148.4 215,-1148.4 215,-968.4 535.04,-968.4 535.04,-1148.4"/>
<text xml:space="preserve" text-anchor="start" x="353.91" y="-1061.4" font-family="Arial" font-size="20.00" fill="#eff6ff">User</text>
<text xml:space="preserve" text-anchor="start" x="330.42" y="-1038.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">Platform user</text>
</g>
<!-- mediaplatform -->
<g id="node2" class="node">
<title>mediaplatform</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="535.04,-502.8 215,-502.8 215,-322.8 535.04,-322.8 535.04,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="308.33" y="-415.8" font-family="Arial" font-size="20.00" fill="#eff6ff">media&#45;platform</text>
<text xml:space="preserve" text-anchor="start" x="321.25" y="-392.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Render platform</text>
</g>
<!-- reviewer -->
<g id="node3" class="node">
<title>reviewer</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="965.04,-1148.4 645,-1148.4 645,-968.4 965.04,-968.4 965.04,-1148.4"/>
<text xml:space="preserve" text-anchor="start" x="728.33" y="-1061.4" font-family="Arial" font-size="20.00" fill="#eff6ff">Human Reviewer</text>
<text xml:space="preserve" text-anchor="start" x="765.42" y="-1038.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">Final arbiter</text>
</g>
<!-- hermes -->
<g id="node4" class="node">
<title>hermes</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="965.04,-825.6 645,-825.6 645,-645.6 965.04,-645.6 965.04,-825.6"/>
<text xml:space="preserve" text-anchor="start" x="706.65" y="-738.6" font-family="Arial" font-size="20.00" fill="#eff6ff">Hermes Control Plane</text>
<text xml:space="preserve" text-anchor="start" x="722.48" y="-715.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">Multi&#45;agent orchestration</text>
</g>
<!-- cloudflare -->
<g id="node5" class="node">
<title>cloudflare</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="965.04,-502.8 645,-502.8 645,-322.8 965.04,-322.8 965.04,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="759.44" y="-415.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Cloudflare</text>
<text xml:space="preserve" text-anchor="start" x="727.06" y="-392.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">R2, Pages, AI Gateway</text>
</g>
<!-- telegram -->
<g id="node6" class="node">
<title>telegram</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1395.04,-502.8 1075,-502.8 1075,-322.8 1395.04,-322.8 1395.04,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="1192.78" y="-415.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Telegram</text>
<text xml:space="preserve" text-anchor="start" x="1194.17" y="-392.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Notifications</text>
</g>
<!-- aiproviders -->
<g id="node7" class="node">
<title>aiproviders</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-180 0,-180 0,0 320.04,0 320.04,-180"/>
<text xml:space="preserve" text-anchor="start" x="105.56" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">AI Providers</text>
<text xml:space="preserve" text-anchor="start" x="47.88" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">MiMo, LongCat, OpenRouter, etc.</text>
</g>
<!-- storage -->
<g id="node8" class="node">
<title>storage</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="750.04,-180 430,-180 430,0 750.04,0 750.04,-180"/>
<text xml:space="preserve" text-anchor="start" x="555" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage</text>
<text xml:space="preserve" text-anchor="start" x="476.64" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Object storage / shared filesystem</text>
</g>
<!-- user&#45;&gt;mediaplatform -->
<g id="edge1" class="edge">
<title>user&#45;&gt;mediaplatform</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M375.02,-968.59C375.02,-849.18 375.02,-637.53 375.02,-513.03"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="377.65,-513.26 375.02,-505.76 372.4,-513.26 377.65,-513.26"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="375.02,-724.2 375.02,-747 410.59,-747 410.59,-724.2 375.02,-724.2"/>
<text xml:space="preserve" text-anchor="start" x="378.02" y="-730" font-family="Arial" font-size="14.00" fill="#c9c9c9">uses</text>
</g>
<!-- mediaplatform&#45;&gt;aiproviders -->
<g id="edge6" class="edge">
<title>mediaplatform&#45;&gt;aiproviders</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M315.42,-322.87C287.45,-281.14 254.06,-231.31 225.4,-188.56"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="227.59,-187.11 221.24,-182.34 223.23,-190.04 227.59,-187.11"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="273.97,-240 273.97,-262.8 356.99,-262.8 356.99,-240 273.97,-240"/>
<text xml:space="preserve" text-anchor="start" x="276.97" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">calls models</text>
</g>
<!-- mediaplatform&#45;&gt;storage -->
<g id="edge7" class="edge">
<title>mediaplatform&#45;&gt;storage</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M434.62,-322.87C462.59,-281.14 495.98,-231.31 524.64,-188.56"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="526.81,-190.04 528.8,-182.34 522.45,-187.11 526.81,-190.04"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="488.97,-240 488.97,-262.8 570.44,-262.8 570.44,-240 488.97,-240"/>
<text xml:space="preserve" text-anchor="start" x="491.97" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">reads/writes</text>
</g>
<!-- reviewer&#45;&gt;hermes -->
<g id="edge2" class="edge">
<title>reviewer&#45;&gt;hermes</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M805.02,-968.47C805.02,-927.27 805.02,-878.16 805.02,-835.77"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="807.65,-835.96 805.02,-828.46 802.4,-835.96 807.65,-835.96"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="805.02,-885.6 805.02,-908.4 858.48,-908.4 858.48,-885.6 805.02,-885.6"/>
<text xml:space="preserve" text-anchor="start" x="808.02" y="-891.4" font-family="Arial" font-size="14.00" fill="#c9c9c9">reviews</text>
</g>
<!-- hermes&#45;&gt;mediaplatform -->
<g id="edge5" class="edge">
<title>hermes&#45;&gt;mediaplatform</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M685.81,-645.67C628.72,-603.07 560.3,-552.03 502.21,-508.69"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="504.09,-506.82 496.51,-504.44 500.95,-511.03 504.09,-506.82"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="602.91,-562.8 602.91,-585.6 664.95,-585.6 664.95,-562.8 602.91,-562.8"/>
<text xml:space="preserve" text-anchor="start" x="605.91" y="-568.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">develops</text>
</g>
<!-- hermes&#45;&gt;cloudflare -->
<g id="edge3" class="edge">
<title>hermes&#45;&gt;cloudflare</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M805.02,-645.67C805.02,-604.47 805.02,-555.36 805.02,-512.97"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="807.65,-513.16 805.02,-505.66 802.4,-513.16 807.65,-513.16"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="805.02,-562.8 805.02,-585.6 870.17,-585.6 870.17,-562.8 805.02,-562.8"/>
<text xml:space="preserve" text-anchor="start" x="808.02" y="-568.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">publishes</text>
</g>
<!-- hermes&#45;&gt;telegram -->
<g id="edge4" class="edge">
<title>hermes&#45;&gt;telegram</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M924.23,-645.67C981.32,-603.07 1049.74,-552.03 1107.83,-508.69"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1109.09,-511.03 1113.53,-504.44 1105.95,-506.82 1109.09,-511.03"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1032.91,-562.8 1032.91,-585.6 1083.27,-585.6 1083.27,-562.8 1032.91,-562.8"/>
<text xml:space="preserve" text-anchor="start" x="1035.91" y="-568.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">notifies</text>
</g>
</g>
</svg>
`;case`systemContext`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="478pt" height="627pt"
 viewBox="0.00 0.00 478.00 627.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 612.25)">
<g id="clust1" class="cluster">
<title>cluster_mediaplatform</title>
<polygon fill="#194b9e" stroke="#1b3d88" points="8,-8 8,-589.2 440,-589.2 440,-8 8,-8"/>
<text xml:space="preserve" text-anchor="start" x="16" y="-576.3" font-family="Arial" font-weight="bold" font-size="11.00" fill="#bfdbfe" fill-opacity="0.701961">MEDIA&#45;PLATFORM</text>
</g>
<!-- opencue -->
<g id="node1" class="node">
<title>opencue</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="397.45,-528 50.55,-528 50.55,-348 397.45,-348 397.45,-528"/>
<text xml:space="preserve" text-anchor="start" x="181.19" y="-441" font-family="Arial" font-size="20.00" fill="#eff6ff">OpenCue</text>
<text xml:space="preserve" text-anchor="start" x="70.6" y="-418" font-family="Arial" font-size="15.00" fill="#bfdbfe">ExecutionEnvironment only — NOT a Provider</text>
</g>
<!-- remotion -->
<g id="node2" class="node">
<title>remotion</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="399.55,-228 48.45,-228 48.45,-48 399.55,-48 399.55,-228"/>
<text xml:space="preserve" text-anchor="start" x="181.2" y="-141" font-family="Arial" font-size="20.00" fill="#eff6ff">Remotion</text>
<text xml:space="preserve" text-anchor="start" x="68.51" y="-118" font-family="Arial" font-size="15.00" fill="#bfdbfe">Non&#45;production/POC subtitle template provider</text>
</g>
<!-- opencue&#45;&gt;remotion -->
</g>
</svg>
`;case`containerDiagram`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="1746pt" height="973pt"
 viewBox="0.00 0.00 1746.00 973.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 957.85)">
<g id="clust1" class="cluster">
<title>cluster_mediaplatform</title>
<polygon fill="#194b9e" stroke="#1b3d88" points="8,-8 8,-934.8 1326,-934.8 1326,-8 8,-8"/>
<text xml:space="preserve" text-anchor="start" x="16" y="-921.9" font-family="Arial" font-weight="bold" font-size="11.00" fill="#bfdbfe" fill-opacity="0.701961">MEDIA&#45;PLATFORM</text>
</g>
<!-- platformapp -->
<g id="node1" class="node">
<title>platformapp</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="811.02,-873.6 490.98,-873.6 490.98,-693.6 811.02,-693.6 811.02,-873.6"/>
<text xml:space="preserve" text-anchor="start" x="594.86" y="-786.6" font-family="Arial" font-size="20.00" fill="#eff6ff">platform&#45;app</text>
<text xml:space="preserve" text-anchor="start" x="574.7" y="-763.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">Spring Boot entry point</text>
</g>
<!-- rendermodule -->
<g id="node2" class="node">
<title>rendermodule</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="811.02,-550.8 490.98,-550.8 490.98,-370.8 811.02,-370.8 811.02,-550.8"/>
<text xml:space="preserve" text-anchor="start" x="585.97" y="-463.8" font-family="Arial" font-size="20.00" fill="#eff6ff">render&#45;module</text>
<text xml:space="preserve" text-anchor="start" x="584.3" y="-440.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Core render domain</text>
</g>
<!-- aimodule -->
<g id="node3" class="node">
<title>aimodule</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="375.02,-550.8 54.98,-550.8 54.98,-370.8 375.02,-370.8 375.02,-550.8"/>
<text xml:space="preserve" text-anchor="start" x="171.09" y="-463.8" font-family="Arial" font-size="20.00" fill="#eff6ff">ai&#45;module</text>
<text xml:space="preserve" text-anchor="start" x="170.81" y="-440.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">AI integration</text>
</g>
<!-- opencue -->
<g id="node4" class="node">
<title>opencue</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="824.45,-228 477.55,-228 477.55,-48 824.45,-48 824.45,-228"/>
<text xml:space="preserve" text-anchor="start" x="608.19" y="-141" font-family="Arial" font-size="20.00" fill="#eff6ff">OpenCue</text>
<text xml:space="preserve" text-anchor="start" x="497.6" y="-118" font-family="Arial" font-size="15.00" fill="#bfdbfe">ExecutionEnvironment only — NOT a Provider</text>
</g>
<!-- remotion -->
<g id="node5" class="node">
<title>remotion</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1285.55,-228 934.45,-228 934.45,-48 1285.55,-48 1285.55,-228"/>
<text xml:space="preserve" text-anchor="start" x="1067.2" y="-141" font-family="Arial" font-size="20.00" fill="#eff6ff">Remotion</text>
<text xml:space="preserve" text-anchor="start" x="954.51" y="-118" font-family="Arial" font-size="15.00" fill="#bfdbfe">Non&#45;production/POC subtitle template provider</text>
</g>
<!-- sharedkernel -->
<g id="node6" class="node">
<title>sharedkernel</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-228 47.98,-228 47.98,-48 368.02,-48 368.02,-228"/>
<text xml:space="preserve" text-anchor="start" x="146.86" y="-141" font-family="Arial" font-size="20.00" fill="#eff6ff">shared&#45;kernel</text>
<text xml:space="preserve" text-anchor="start" x="123.38" y="-118" font-family="Arial" font-size="15.00" fill="#bfdbfe">Shared domain primitives</text>
</g>
<!-- storage -->
<g id="node7" class="node">
<title>storage</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1716.02,-228 1395.98,-228 1395.98,-48 1716.02,-48 1716.02,-228"/>
<text xml:space="preserve" text-anchor="start" x="1520.98" y="-141" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage</text>
<text xml:space="preserve" text-anchor="start" x="1442.62" y="-118" font-family="Arial" font-size="15.00" fill="#bfdbfe">Object storage / shared filesystem</text>
</g>
<!-- platformapp&#45;&gt;rendermodule -->
<g id="edge1" class="edge">
<title>platformapp&#45;&gt;rendermodule</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M651,-693.67C651,-652.47 651,-603.36 651,-560.97"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="653.63,-561.16 651,-553.66 648.38,-561.16 653.63,-561.16"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="651,-610.8 651,-633.6 686.57,-633.6 686.57,-610.8 651,-610.8"/>
<text xml:space="preserve" text-anchor="start" x="654" y="-616.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">uses</text>
</g>
<!-- platformapp&#45;&gt;aimodule -->
<g id="edge2" class="edge">
<title>platformapp&#45;&gt;aimodule</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M530.13,-693.67C472.24,-651.07 402.87,-600.03 343.97,-556.69"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="345.76,-554.75 338.17,-552.42 342.65,-558.98 345.76,-554.75"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="446.07,-610.8 446.07,-633.6 481.65,-633.6 481.65,-610.8 446.07,-610.8"/>
<text xml:space="preserve" text-anchor="start" x="449.07" y="-616.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">uses</text>
</g>
<!-- rendermodule&#45;&gt;opencue -->
<g id="edge3" class="edge">
<title>rendermodule&#45;&gt;opencue</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M651,-370.87C651,-329.67 651,-280.56 651,-238.17"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="653.63,-238.36 651,-230.86 648.38,-238.36 653.63,-238.36"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="651,-288 651,-310.8 814.98,-310.8 814.98,-288 651,-288"/>
<text xml:space="preserve" text-anchor="start" x="654" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">routes ExecutionEnv only</text>
</g>
<!-- rendermodule&#45;&gt;remotion -->
<g id="edge4" class="edge">
<title>rendermodule&#45;&gt;remotion</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M778.25,-370.87C839.31,-328.19 912.52,-277.02 974.61,-233.63"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="975.78,-236.01 980.42,-229.56 972.77,-231.71 975.78,-236.01"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="894.26,-288 894.26,-310.8 1002.97,-310.8 1002.97,-288 894.26,-288"/>
<text xml:space="preserve" text-anchor="start" x="897.26" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">routes POC only</text>
</g>
<!-- rendermodule&#45;&gt;sharedkernel -->
<g id="edge6" class="edge">
<title>rendermodule&#45;&gt;sharedkernel</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M528.19,-370.87C469.37,-328.27 398.89,-277.23 339.04,-233.89"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="340.73,-231.88 333.12,-229.6 337.65,-236.13 340.73,-231.88"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="442.78,-288 442.78,-310.8 521.96,-310.8 521.96,-288 442.78,-288"/>
<text xml:space="preserve" text-anchor="start" x="445.78" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">depends on</text>
</g>
<!-- rendermodule&#45;&gt;storage -->
<g id="edge5" class="edge">
<title>rendermodule&#45;&gt;storage</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M810.73,-411.77C954,-367.58 1169.34,-298.4 1353,-228 1364.02,-223.78 1375.31,-219.29 1386.65,-214.66"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1387.44,-217.17 1393.38,-211.89 1385.45,-212.31 1387.44,-217.17"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1181.56,-288 1181.56,-310.8 1288.69,-310.8 1288.69,-288 1181.56,-288"/>
<text xml:space="preserve" text-anchor="start" x="1184.56" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">persists artifacts</text>
</g>
<!-- aimodule&#45;&gt;sharedkernel -->
<g id="edge7" class="edge">
<title>aimodule&#45;&gt;sharedkernel</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M213.06,-370.87C212.16,-329.67 211.09,-280.56 210.16,-238.17"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="212.79,-238.3 210,-230.86 207.54,-238.41 212.79,-238.3"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="211.71,-288 211.71,-310.8 290.89,-310.8 290.89,-288 211.71,-288"/>
<text xml:space="preserve" text-anchor="start" x="214.71" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">depends on</text>
</g>
</g>
</svg>
`;case`controlPlane`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="1339pt" height="627pt"
 viewBox="0.00 0.00 1339.00 627.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 612.25)">
<g id="clust1" class="cluster">
<title>cluster_hermes</title>
<polygon fill="#194b9e" stroke="#1b3d88" points="8,-8 8,-589.2 1301,-589.2 1301,-8 8,-8"/>
<text xml:space="preserve" text-anchor="start" x="16" y="-576.3" font-family="Arial" font-weight="bold" font-size="11.00" fill="#bfdbfe" fill-opacity="0.701961">HERMES CONTROL PLANE</text>
</g>
<!-- hermesagent -->
<g id="node1" class="node">
<title>hermesagent</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-528 47.98,-528 47.98,-348 368.02,-348 368.02,-528"/>
<text xml:space="preserve" text-anchor="start" x="144.08" y="-450" font-family="Arial" font-size="20.00" fill="#eff6ff">Hermes Agent</text>
<text xml:space="preserve" text-anchor="start" x="77.94" y="-427" font-family="Arial" font-size="15.00" fill="#bfdbfe">Gateway, orchestrator, Level 3 Feature</text>
<text xml:space="preserve" text-anchor="start" x="168.81" y="-409" font-family="Arial" font-size="15.00" fill="#bfdbfe">Coordinator</text>
</g>
<!-- codingagents -->
<g id="node2" class="node">
<title>codingagents</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="815.9,-528 478.1,-528 478.1,-348 815.9,-348 815.9,-528"/>
<text xml:space="preserve" text-anchor="start" x="581.4" y="-450" font-family="Arial" font-size="20.00" fill="#eff6ff">Coding Agents</text>
<text xml:space="preserve" text-anchor="start" x="498.16" y="-427" font-family="Arial" font-size="15.00" fill="#bfdbfe">OpenCode, Codex, Kilo Code, Claude Code,</text>
<text xml:space="preserve" text-anchor="start" x="629.49" y="-409" font-family="Arial" font-size="15.00" fill="#bfdbfe">Aider</text>
</g>
<!-- reviewinfra -->
<g id="node3" class="node">
<title>reviewinfra</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1260.6,-528 925.4,-528 925.4,-348 1260.6,-348 1260.6,-528"/>
<text xml:space="preserve" text-anchor="start" x="998.52" y="-441" font-family="Arial" font-size="20.00" fill="#eff6ff">Review Infrastructure</text>
<text xml:space="preserve" text-anchor="start" x="945.46" y="-418" font-family="Arial" font-size="15.00" fill="#bfdbfe">CODEOWNERS, Semgrep, Review Packets</text>
</g>
<!-- policies -->
<g id="node4" class="node">
<title>policies</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-228 47.98,-228 47.98,-48 368.02,-48 368.02,-228"/>
<text xml:space="preserve" text-anchor="start" x="173.54" y="-141" font-family="Arial" font-size="20.00" fill="#eff6ff">Policies</text>
<text xml:space="preserve" text-anchor="start" x="112.96" y="-118" font-family="Arial" font-size="15.00" fill="#bfdbfe">Permissions, stop conditions</text>
</g>
<!-- dashboard -->
<g id="node5" class="node">
<title>dashboard</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1253.02,-228 932.98,-228 932.98,-48 1253.02,-48 1253.02,-228"/>
<text xml:space="preserve" text-anchor="start" x="1044.08" y="-141" font-family="Arial" font-size="20.00" fill="#eff6ff">Dashboard</text>
<text xml:space="preserve" text-anchor="start" x="1053.41" y="-118" font-family="Arial" font-size="15.00" fill="#bfdbfe">scribe.cc.cd</text>
</g>
<!-- hermesagent&#45;&gt;policies -->
<!-- codingagents&#45;&gt;reviewinfra -->
<!-- reviewinfra&#45;&gt;dashboard -->
</g>
</svg>
`;case`controlPlaneContext`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="2010pt" height="1122pt"
 viewBox="0.00 0.00 2010.00 1122.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 1107.25)">
<g id="clust1" class="cluster">
<title>cluster_hermes</title>
<polygon fill="#194b9e" stroke="#1b3d88" points="8,-261 8,-843.2 1411,-843.2 1411,-261 8,-261"/>
<text xml:space="preserve" text-anchor="start" x="16" y="-830.3" font-family="Arial" font-weight="bold" font-size="11.00" fill="#bfdbfe" fill-opacity="0.701961">HERMES CONTROL PLANE</text>
</g>
<!-- hermesagent -->
<g id="node1" class="node">
<title>hermesagent</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-782 47.98,-782 47.98,-602 368.02,-602 368.02,-782"/>
<text xml:space="preserve" text-anchor="start" x="144.08" y="-704" font-family="Arial" font-size="20.00" fill="#eff6ff">Hermes Agent</text>
<text xml:space="preserve" text-anchor="start" x="77.94" y="-681" font-family="Arial" font-size="15.00" fill="#bfdbfe">Gateway, orchestrator, Level 3 Feature</text>
<text xml:space="preserve" text-anchor="start" x="168.81" y="-663" font-family="Arial" font-size="15.00" fill="#bfdbfe">Coordinator</text>
</g>
<!-- codingagents -->
<g id="node2" class="node">
<title>codingagents</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="815.9,-782 478.1,-782 478.1,-602 815.9,-602 815.9,-782"/>
<text xml:space="preserve" text-anchor="start" x="581.4" y="-704" font-family="Arial" font-size="20.00" fill="#eff6ff">Coding Agents</text>
<text xml:space="preserve" text-anchor="start" x="498.16" y="-681" font-family="Arial" font-size="15.00" fill="#bfdbfe">OpenCode, Codex, Kilo Code, Claude Code,</text>
<text xml:space="preserve" text-anchor="start" x="629.49" y="-663" font-family="Arial" font-size="15.00" fill="#bfdbfe">Aider</text>
</g>
<!-- reviewinfra -->
<g id="node3" class="node">
<title>reviewinfra</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1370.6,-782 1035.4,-782 1035.4,-602 1370.6,-602 1370.6,-782"/>
<text xml:space="preserve" text-anchor="start" x="1108.52" y="-695" font-family="Arial" font-size="20.00" fill="#eff6ff">Review Infrastructure</text>
<text xml:space="preserve" text-anchor="start" x="1055.46" y="-672" font-family="Arial" font-size="15.00" fill="#bfdbfe">CODEOWNERS, Semgrep, Review Packets</text>
</g>
<!-- policies -->
<g id="node4" class="node">
<title>policies</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-481 47.98,-481 47.98,-301 368.02,-301 368.02,-481"/>
<text xml:space="preserve" text-anchor="start" x="173.54" y="-394" font-family="Arial" font-size="20.00" fill="#eff6ff">Policies</text>
<text xml:space="preserve" text-anchor="start" x="112.96" y="-371" font-family="Arial" font-size="15.00" fill="#bfdbfe">Permissions, stop conditions</text>
</g>
<!-- dashboard -->
<g id="node5" class="node">
<title>dashboard</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1084.02,-481 763.98,-481 763.98,-301 1084.02,-301 1084.02,-481"/>
<text xml:space="preserve" text-anchor="start" x="875.08" y="-394" font-family="Arial" font-size="20.00" fill="#eff6ff">Dashboard</text>
<text xml:space="preserve" text-anchor="start" x="884.41" y="-371" font-family="Arial" font-size="15.00" fill="#bfdbfe">scribe.cc.cd</text>
</g>
<!-- cloudflare -->
<g id="node6" class="node">
<title>cloudflare</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="654.02,-180 333.98,-180 333.98,0 654.02,0 654.02,-180"/>
<text xml:space="preserve" text-anchor="start" x="448.42" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Cloudflare</text>
<text xml:space="preserve" text-anchor="start" x="416.04" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">R2, Pages, AI Gateway</text>
</g>
<!-- telegram -->
<g id="node7" class="node">
<title>telegram</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1084.02,-180 763.98,-180 763.98,0 1084.02,0 1084.02,-180"/>
<text xml:space="preserve" text-anchor="start" x="881.76" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Telegram</text>
<text xml:space="preserve" text-anchor="start" x="883.15" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Notifications</text>
</g>
<!-- mediaplatform -->
<g id="node8" class="node">
<title>mediaplatform</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1980.02,-180 1659.98,-180 1659.98,0 1980.02,0 1980.02,-180"/>
<text xml:space="preserve" text-anchor="start" x="1753.31" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">media&#45;platform</text>
<text xml:space="preserve" text-anchor="start" x="1766.23" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Render platform</text>
</g>
<!-- user -->
<g id="node9" class="node">
<title>user</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1514.02,-180 1193.98,-180 1193.98,0 1514.02,0 1514.02,-180"/>
<text xml:space="preserve" text-anchor="start" x="1332.89" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">User</text>
<text xml:space="preserve" text-anchor="start" x="1309.4" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Platform user</text>
</g>
<!-- reviewer -->
<g id="node10" class="node">
<title>reviewer</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-1092.2 47.98,-1092.2 47.98,-912.2 368.02,-912.2 368.02,-1092.2"/>
<text xml:space="preserve" text-anchor="start" x="131.31" y="-1005.2" font-family="Arial" font-size="20.00" fill="#eff6ff">Human Reviewer</text>
<text xml:space="preserve" text-anchor="start" x="168.4" y="-982.2" font-family="Arial" font-size="15.00" fill="#bfdbfe">Final arbiter</text>
</g>
<!-- hermesagent&#45;&gt;policies -->
<!-- codingagents&#45;&gt;reviewinfra -->
<!-- dashboard&#45;&gt;cloudflare -->
<g id="edge3" class="edge">
<title>dashboard&#45;&gt;cloudflare</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M738.49,-261C702.11,-235.71 664.59,-209.62 630.19,-185.7"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="631.87,-183.67 624.21,-181.54 628.87,-187.98 631.87,-183.67"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="614.6,-220.16 614.6,-242.96 679.75,-242.96 679.75,-220.16 614.6,-220.16"/>
<text xml:space="preserve" text-anchor="start" x="617.6" y="-225.96" font-family="Arial" font-size="14.00" fill="#c9c9c9">publishes</text>
</g>
<!-- dashboard&#45;&gt;telegram -->
<g id="edge4" class="edge">
<title>dashboard&#45;&gt;telegram</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M924,-261C924,-237.31 924,-212.93 924,-190.28"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="926.63,-190.34 924,-182.84 921.38,-190.34 926.63,-190.34"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="873.64,-220.25 873.64,-243.05 924,-243.05 924,-220.25 873.64,-220.25"/>
<text xml:space="preserve" text-anchor="start" x="876.64" y="-226.05" font-family="Arial" font-size="14.00" fill="#c9c9c9">notifies</text>
</g>
<!-- dashboard&#45;&gt;mediaplatform -->
<g id="edge5" class="edge">
<title>dashboard&#45;&gt;mediaplatform</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M1325.69,-261C1406.39,-234.59 1490.5,-206.69 1569,-180 1595.43,-171.01 1623.36,-161.31 1650.6,-151.74"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1651.15,-154.33 1657.35,-149.36 1649.41,-149.38 1651.15,-154.33"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1431.04,-205.61 1431.04,-228.41 1493.09,-228.41 1493.09,-205.61 1431.04,-205.61"/>
<text xml:space="preserve" text-anchor="start" x="1434.04" y="-211.41" font-family="Arial" font-size="14.00" fill="#c9c9c9">develops</text>
</g>
<!-- user&#45;&gt;mediaplatform -->
<g id="edge6" class="edge">
<title>user&#45;&gt;mediaplatform</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M1513.72,-90C1557.59,-90 1605.33,-90 1649.77,-90"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="1649.57,-92.63 1657.07,-90 1649.57,-87.38 1649.57,-92.63"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="1569.21,-93 1569.21,-115.8 1604.79,-115.8 1604.79,-93 1569.21,-93"/>
<text xml:space="preserve" text-anchor="start" x="1572.21" y="-98.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">uses</text>
</g>
<!-- reviewer&#45;&gt;hermesagent -->
<g id="edge7" class="edge">
<title>reviewer&#45;&gt;hermesagent</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M208,-912.47C208,-893.89 208,-873.76 208,-853.48"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="210.63,-853.73 208,-846.23 205.38,-853.73 210.63,-853.73"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="154.54,-877.56 154.54,-900.36 208,-900.36 208,-877.56 154.54,-877.56"/>
<text xml:space="preserve" text-anchor="start" x="157.54" y="-883.36" font-family="Arial" font-size="14.00" fill="#c9c9c9">reviews</text>
</g>
</g>
</svg>
`;case`vs0VerticalSlice`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="494pt" height="2539pt"
 viewBox="0.00 0.00 494.00 2539.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 2523.85)">
<g id="clust1" class="cluster">
<title>cluster_rendermodule</title>
<polygon fill="#194b9e" stroke="#1b3d88" points="8,-282.8 8,-2500.8 456,-2500.8 456,-282.8 8,-282.8"/>
<text xml:space="preserve" text-anchor="start" x="16" y="-2487.9" font-family="Arial" font-weight="bold" font-size="11.00" fill="#bfdbfe" fill-opacity="0.701961">RENDER&#45;MODULE</text>
</g>
<!-- timelineedit -->
<g id="node1" class="node">
<title>timelineedit</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="406.73,-2439.6 57.27,-2439.6 57.27,-2259.6 406.73,-2259.6 406.73,-2439.6"/>
<text xml:space="preserve" text-anchor="start" x="125.3" y="-2361.6" font-family="Arial" font-size="20.00" fill="#eff6ff">Timeline Edit Command</text>
<text xml:space="preserve" text-anchor="start" x="77.33" y="-2338.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">TL.0: Sealed interface with 12 typed command</text>
<text xml:space="preserve" text-anchor="start" x="206.99" y="-2320.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">records</text>
</g>
<!-- captiontemplate -->
<g id="node2" class="node">
<title>captiontemplate</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="399.21,-2116.8 64.79,-2116.8 64.79,-1936.8 399.21,-1936.8 399.21,-2116.8"/>
<text xml:space="preserve" text-anchor="start" x="153.07" y="-2038.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Caption Template</text>
<text xml:space="preserve" text-anchor="start" x="84.84" y="-2015.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">CT.0: Typed intent model for caption/subtitle</text>
<text xml:space="preserve" text-anchor="start" x="200.31" y="-1997.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">rendering</text>
</g>
<!-- assstylemapper -->
<g id="node3" class="node">
<title>assstylemapper</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="415.88,-1794 48.12,-1794 48.12,-1614 415.88,-1614 415.88,-1794"/>
<text xml:space="preserve" text-anchor="start" x="159.19" y="-1716" font-family="Arial" font-size="20.00" fill="#eff6ff">AssStyleMapper</text>
<text xml:space="preserve" text-anchor="start" x="68.18" y="-1693" font-family="Arial" font-size="15.00" fill="#bfdbfe">CT.0: Maps CaptionTemplateSpec to ASS format</text>
<text xml:space="preserve" text-anchor="start" x="194.07" y="-1675" font-family="Arial" font-size="15.00" fill="#bfdbfe">parameters</text>
</g>
<!-- providerbinding -->
<g id="node4" class="node">
<title>providerbinding</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="392.02,-1471.2 71.98,-1471.2 71.98,-1291.2 392.02,-1291.2 392.02,-1471.2"/>
<text xml:space="preserve" text-anchor="start" x="158.63" y="-1393.2" font-family="Arial" font-size="20.00" fill="#eff6ff">Provider Binding</text>
<text xml:space="preserve" text-anchor="start" x="98" y="-1370.2" font-family="Arial" font-size="15.00" fill="#bfdbfe">Deterministic eligibility + priority provider</text>
<text xml:space="preserve" text-anchor="start" x="202.4" y="-1352.2" font-family="Arial" font-size="15.00" fill="#bfdbfe">selection</text>
</g>
<!-- ffmpegbaseline -->
<g id="node5" class="node">
<title>ffmpegbaseline</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="392.02,-1148.4 71.98,-1148.4 71.98,-968.4 392.02,-968.4 392.02,-1148.4"/>
<text xml:space="preserve" text-anchor="start" x="125.29" y="-1061.4" font-family="Arial" font-size="20.00" fill="#eff6ff">FFmpeg/libass Baseline</text>
<text xml:space="preserve" text-anchor="start" x="132.35" y="-1038.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">Production rendering baseline</text>
</g>
<!-- productruntime -->
<g id="node6" class="node">
<title>productruntime</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="392.02,-825.6 71.98,-825.6 71.98,-645.6 392.02,-645.6 392.02,-825.6"/>
<text xml:space="preserve" text-anchor="start" x="157.52" y="-738.6" font-family="Arial" font-size="20.00" fill="#eff6ff">Product Runtime</text>
<text xml:space="preserve" text-anchor="start" x="131.53" y="-715.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">Product lifecycle management</text>
</g>
<!-- storageruntime -->
<g id="node7" class="node">
<title>storageruntime</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="392.02,-502.8 71.98,-502.8 71.98,-322.8 392.02,-322.8 392.02,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="156.96" y="-415.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage Runtime</text>
<text xml:space="preserve" text-anchor="start" x="109.43" y="-392.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Storage/materialization management</text>
</g>
<!-- storage -->
<g id="node8" class="node">
<title>storage</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="392.02,-180 71.98,-180 71.98,0 392.02,0 392.02,-180"/>
<text xml:space="preserve" text-anchor="start" x="196.98" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage</text>
<text xml:space="preserve" text-anchor="start" x="118.62" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Object storage / shared filesystem</text>
</g>
<!-- timelineedit&#45;&gt;captiontemplate -->
<g id="edge1" class="edge">
<title>timelineedit&#45;&gt;captiontemplate</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M232,-2259.67C232,-2218.47 232,-2169.36 232,-2126.97"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="234.63,-2127.16 232,-2119.66 229.38,-2127.16 234.63,-2127.16"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="232,-2176.8 232,-2199.6 376.54,-2199.6 376.54,-2176.8 232,-2176.8"/>
<text xml:space="preserve" text-anchor="start" x="235" y="-2182.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">generates typed intent</text>
</g>
<!-- captiontemplate&#45;&gt;assstylemapper -->
<g id="edge2" class="edge">
<title>captiontemplate&#45;&gt;assstylemapper</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M232,-1936.87C232,-1895.67 232,-1846.56 232,-1804.17"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="234.63,-1804.36 232,-1796.86 229.38,-1804.36 234.63,-1804.36"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="232,-1854 232,-1876.8 394.4,-1876.8 394.4,-1854 232,-1854"/>
<text xml:space="preserve" text-anchor="start" x="235" y="-1859.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">maps to ASS parameters</text>
</g>
<!-- assstylemapper&#45;&gt;providerbinding -->
<g id="edge3" class="edge">
<title>assstylemapper&#45;&gt;providerbinding</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M232,-1614.07C232,-1572.87 232,-1523.76 232,-1481.37"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="234.63,-1481.56 232,-1474.06 229.38,-1481.56 234.63,-1481.56"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="232,-1531.2 232,-1554 344.6,-1554 344.6,-1531.2 232,-1531.2"/>
<text xml:space="preserve" text-anchor="start" x="235" y="-1537" font-family="Arial" font-size="14.00" fill="#c9c9c9">resolves provider</text>
</g>
<!-- providerbinding&#45;&gt;ffmpegbaseline -->
<g id="edge4" class="edge">
<title>providerbinding&#45;&gt;ffmpegbaseline</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M232,-1291.27C232,-1250.07 232,-1200.96 232,-1158.57"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="234.63,-1158.76 232,-1151.26 229.38,-1158.76 234.63,-1158.76"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="232,-1208.4 232,-1231.2 374.91,-1231.2 374.91,-1208.4 232,-1208.4"/>
<text xml:space="preserve" text-anchor="start" x="235" y="-1214.2" font-family="Arial" font-size="14.00" fill="#c9c9c9">routes PRODUCTION</text>
</g>
<!-- ffmpegbaseline&#45;&gt;productruntime -->
<g id="edge5" class="edge">
<title>ffmpegbaseline&#45;&gt;productruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M232,-968.47C232,-927.27 232,-878.16 232,-835.77"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="234.63,-835.96 232,-828.46 229.38,-835.96 234.63,-835.96"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="232,-885.6 232,-908.4 338.41,-908.4 338.41,-885.6 232,-885.6"/>
<text xml:space="preserve" text-anchor="start" x="235" y="-891.4" font-family="Arial" font-size="14.00" fill="#c9c9c9">produces output</text>
</g>
<!-- productruntime&#45;&gt;storageruntime -->
<g id="edge6" class="edge">
<title>productruntime&#45;&gt;storageruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M232,-645.67C232,-604.47 232,-555.36 232,-512.97"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="234.63,-513.16 232,-505.66 229.38,-513.16 234.63,-513.16"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="232,-562.8 232,-585.6 349.28,-585.6 349.28,-562.8 232,-562.8"/>
<text xml:space="preserve" text-anchor="start" x="235" y="-568.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">manages lifecycle</text>
</g>
<!-- storageruntime&#45;&gt;storage -->
<g id="edge7" class="edge">
<title>storageruntime&#45;&gt;storage</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M232,-322.87C232,-281.67 232,-232.56 232,-190.17"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="234.63,-190.36 232,-182.86 229.38,-190.36 234.63,-190.36"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="232,-240 232,-262.8 339.14,-262.8 339.14,-240 232,-240"/>
<text xml:space="preserve" text-anchor="start" x="235" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">persists artifacts</text>
</g>
</g>
</svg>
`;case`captionTemplateBoundary`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="398pt" height="1501pt"
 viewBox="0.00 0.00 398.00 1501.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 1486.25)">
<!-- timelineedit -->
<g id="node1" class="node">
<title>timelineedit</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="358.6,-1471.2 9.15,-1471.2 9.15,-1291.2 358.6,-1291.2 358.6,-1471.2"/>
<text xml:space="preserve" text-anchor="start" x="77.17" y="-1393.2" font-family="Arial" font-size="20.00" fill="#eff6ff">Timeline Edit Command</text>
<text xml:space="preserve" text-anchor="start" x="29.21" y="-1370.2" font-family="Arial" font-size="15.00" fill="#bfdbfe">TL.0: Sealed interface with 12 typed command</text>
<text xml:space="preserve" text-anchor="start" x="158.87" y="-1352.2" font-family="Arial" font-size="15.00" fill="#bfdbfe">records</text>
</g>
<!-- captiontemplate -->
<g id="node2" class="node">
<title>captiontemplate</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="351.09,-1148.4 16.66,-1148.4 16.66,-968.4 351.09,-968.4 351.09,-1148.4"/>
<text xml:space="preserve" text-anchor="start" x="104.95" y="-1070.4" font-family="Arial" font-size="20.00" fill="#eff6ff">Caption Template</text>
<text xml:space="preserve" text-anchor="start" x="36.72" y="-1047.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">CT.0: Typed intent model for caption/subtitle</text>
<text xml:space="preserve" text-anchor="start" x="152.19" y="-1029.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">rendering</text>
</g>
<!-- assstylemapper -->
<g id="node3" class="node">
<title>assstylemapper</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="367.75,-825.6 0,-825.6 0,-645.6 367.75,-645.6 367.75,-825.6"/>
<text xml:space="preserve" text-anchor="start" x="111.07" y="-747.6" font-family="Arial" font-size="20.00" fill="#eff6ff">AssStyleMapper</text>
<text xml:space="preserve" text-anchor="start" x="20.06" y="-724.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">CT.0: Maps CaptionTemplateSpec to ASS format</text>
<text xml:space="preserve" text-anchor="start" x="145.94" y="-706.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">parameters</text>
</g>
<!-- providerbinding -->
<g id="node4" class="node">
<title>providerbinding</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="343.9,-502.8 23.86,-502.8 23.86,-322.8 343.9,-322.8 343.9,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="110.5" y="-424.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Provider Binding</text>
<text xml:space="preserve" text-anchor="start" x="49.88" y="-401.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Deterministic eligibility + priority provider</text>
<text xml:space="preserve" text-anchor="start" x="154.28" y="-383.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">selection</text>
</g>
<!-- ffmpegbaseline -->
<g id="node5" class="node">
<title>ffmpegbaseline</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="343.9,-180 23.86,-180 23.86,0 343.9,0 343.9,-180"/>
<text xml:space="preserve" text-anchor="start" x="77.16" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">FFmpeg/libass Baseline</text>
<text xml:space="preserve" text-anchor="start" x="84.23" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Production rendering baseline</text>
</g>
<!-- timelineedit&#45;&gt;captiontemplate -->
<g id="edge1" class="edge">
<title>timelineedit&#45;&gt;captiontemplate</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M183.88,-1291.27C183.88,-1250.07 183.88,-1200.96 183.88,-1158.57"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="186.5,-1158.76 183.88,-1151.26 181.25,-1158.76 186.5,-1158.76"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="183.88,-1208.4 183.88,-1231.2 328.42,-1231.2 328.42,-1208.4 183.88,-1208.4"/>
<text xml:space="preserve" text-anchor="start" x="186.88" y="-1214.2" font-family="Arial" font-size="14.00" fill="#c9c9c9">generates typed intent</text>
</g>
<!-- captiontemplate&#45;&gt;assstylemapper -->
<g id="edge2" class="edge">
<title>captiontemplate&#45;&gt;assstylemapper</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M183.88,-968.47C183.88,-927.27 183.88,-878.16 183.88,-835.77"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="186.5,-835.96 183.88,-828.46 181.25,-835.96 186.5,-835.96"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="183.88,-885.6 183.88,-908.4 346.28,-908.4 346.28,-885.6 183.88,-885.6"/>
<text xml:space="preserve" text-anchor="start" x="186.88" y="-891.4" font-family="Arial" font-size="14.00" fill="#c9c9c9">maps to ASS parameters</text>
</g>
<!-- assstylemapper&#45;&gt;providerbinding -->
<g id="edge3" class="edge">
<title>assstylemapper&#45;&gt;providerbinding</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M183.88,-645.67C183.88,-604.47 183.88,-555.36 183.88,-512.97"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="186.5,-513.16 183.88,-505.66 181.25,-513.16 186.5,-513.16"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="183.88,-562.8 183.88,-585.6 296.48,-585.6 296.48,-562.8 183.88,-562.8"/>
<text xml:space="preserve" text-anchor="start" x="186.88" y="-568.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">resolves provider</text>
</g>
<!-- providerbinding&#45;&gt;ffmpegbaseline -->
<g id="edge4" class="edge">
<title>providerbinding&#45;&gt;ffmpegbaseline</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M183.88,-322.87C183.88,-281.67 183.88,-232.56 183.88,-190.17"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="186.5,-190.36 183.88,-182.86 181.25,-190.36 186.5,-190.36"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="183.88,-240 183.88,-262.8 326.79,-262.8 326.79,-240 183.88,-240"/>
<text xml:space="preserve" text-anchor="start" x="186.88" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">routes PRODUCTION</text>
</g>
</g>
</svg>
`;case`productStorageBoundary`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="350pt" height="1178pt"
 viewBox="0.00 0.00 350.00 1178.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 1163.45)">
<!-- ffmpegbaseline -->
<g id="node1" class="node">
<title>ffmpegbaseline</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-1148.4 0,-1148.4 0,-968.4 320.04,-968.4 320.04,-1148.4"/>
<text xml:space="preserve" text-anchor="start" x="53.31" y="-1061.4" font-family="Arial" font-size="20.00" fill="#eff6ff">FFmpeg/libass Baseline</text>
<text xml:space="preserve" text-anchor="start" x="60.37" y="-1038.4" font-family="Arial" font-size="15.00" fill="#bfdbfe">Production rendering baseline</text>
</g>
<!-- productruntime -->
<g id="node2" class="node">
<title>productruntime</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-825.6 0,-825.6 0,-645.6 320.04,-645.6 320.04,-825.6"/>
<text xml:space="preserve" text-anchor="start" x="85.54" y="-738.6" font-family="Arial" font-size="20.00" fill="#eff6ff">Product Runtime</text>
<text xml:space="preserve" text-anchor="start" x="59.55" y="-715.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">Product lifecycle management</text>
</g>
<!-- storageruntime -->
<g id="node3" class="node">
<title>storageruntime</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-502.8 0,-502.8 0,-322.8 320.04,-322.8 320.04,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="84.98" y="-415.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage Runtime</text>
<text xml:space="preserve" text-anchor="start" x="37.45" y="-392.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Storage/materialization management</text>
</g>
<!-- storage -->
<g id="node4" class="node">
<title>storage</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-180 0,-180 0,0 320.04,0 320.04,-180"/>
<text xml:space="preserve" text-anchor="start" x="125" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Storage</text>
<text xml:space="preserve" text-anchor="start" x="46.64" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Object storage / shared filesystem</text>
</g>
<!-- ffmpegbaseline&#45;&gt;productruntime -->
<g id="edge1" class="edge">
<title>ffmpegbaseline&#45;&gt;productruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M160.02,-968.47C160.02,-927.27 160.02,-878.16 160.02,-835.77"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="162.65,-835.96 160.02,-828.46 157.4,-835.96 162.65,-835.96"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="160.02,-885.6 160.02,-908.4 266.43,-908.4 266.43,-885.6 160.02,-885.6"/>
<text xml:space="preserve" text-anchor="start" x="163.02" y="-891.4" font-family="Arial" font-size="14.00" fill="#c9c9c9">produces output</text>
</g>
<!-- productruntime&#45;&gt;storageruntime -->
<g id="edge2" class="edge">
<title>productruntime&#45;&gt;storageruntime</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M160.02,-645.67C160.02,-604.47 160.02,-555.36 160.02,-512.97"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="162.65,-513.16 160.02,-505.66 157.4,-513.16 162.65,-513.16"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="160.02,-562.8 160.02,-585.6 277.3,-585.6 277.3,-562.8 160.02,-562.8"/>
<text xml:space="preserve" text-anchor="start" x="163.02" y="-568.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">manages lifecycle</text>
</g>
<!-- storageruntime&#45;&gt;storage -->
<g id="edge3" class="edge">
<title>storageruntime&#45;&gt;storage</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M160.02,-322.87C160.02,-281.67 160.02,-232.56 160.02,-190.17"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="162.65,-190.36 160.02,-182.86 157.4,-190.36 162.65,-190.36"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="160.02,-240 160.02,-262.8 267.16,-262.8 267.16,-240 160.02,-240"/>
<text xml:space="preserve" text-anchor="start" x="163.02" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">persists artifacts</text>
</g>
</g>
</svg>
`;case`providerExecutionBoundary`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="1268pt" height="533pt"
 viewBox="0.00 0.00 1268.00 533.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 517.85)">
<!-- providerbinding -->
<g id="node1" class="node">
<title>providerbinding</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="541.04,-502.8 221,-502.8 221,-322.8 541.04,-322.8 541.04,-502.8"/>
<text xml:space="preserve" text-anchor="start" x="307.65" y="-424.8" font-family="Arial" font-size="20.00" fill="#eff6ff">Provider Binding</text>
<text xml:space="preserve" text-anchor="start" x="247.02" y="-401.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Deterministic eligibility + priority provider</text>
<text xml:space="preserve" text-anchor="start" x="351.42" y="-383.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">selection</text>
</g>
<!-- ffmpegbaseline -->
<g id="node2" class="node">
<title>ffmpegbaseline</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-180 0,-180 0,0 320.04,0 320.04,-180"/>
<text xml:space="preserve" text-anchor="start" x="53.31" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">FFmpeg/libass Baseline</text>
<text xml:space="preserve" text-anchor="start" x="60.37" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Production rendering baseline</text>
</g>
<!-- opencue -->
<g id="node3" class="node">
<title>opencue</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="776.47,-180 429.57,-180 429.57,0 776.47,0 776.47,-180"/>
<text xml:space="preserve" text-anchor="start" x="560.21" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">OpenCue</text>
<text xml:space="preserve" text-anchor="start" x="449.62" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">ExecutionEnvironment only — NOT a Provider</text>
</g>
<!-- remotion -->
<g id="node4" class="node">
<title>remotion</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="1237.57,-180 886.47,-180 886.47,0 1237.57,0 1237.57,-180"/>
<text xml:space="preserve" text-anchor="start" x="1019.22" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">Remotion</text>
<text xml:space="preserve" text-anchor="start" x="906.53" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Non&#45;production/POC subtitle template provider</text>
</g>
<!-- providerbinding&#45;&gt;ffmpegbaseline -->
<g id="edge1" class="edge">
<title>providerbinding&#45;&gt;ffmpegbaseline</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M319.75,-322.87C290.95,-281.06 256.54,-231.11 227.05,-188.29"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="229.35,-187.01 222.93,-182.32 225.03,-189.99 229.35,-187.01"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="277.15,-240 277.15,-262.8 420.06,-262.8 420.06,-240 277.15,-240"/>
<text xml:space="preserve" text-anchor="start" x="280.15" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">routes PRODUCTION</text>
</g>
<!-- providerbinding&#45;&gt;opencue -->
<g id="edge2" class="edge">
<title>providerbinding&#45;&gt;opencue</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M442.56,-322.87C471.5,-281.06 506.06,-231.11 535.69,-188.29"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="537.72,-189.98 539.83,-182.32 533.4,-186.99 537.72,-189.98"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="498.68,-240 498.68,-262.8 662.65,-262.8 662.65,-240 498.68,-240"/>
<text xml:space="preserve" text-anchor="start" x="501.68" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">routes ExecutionEnv only</text>
</g>
<!-- providerbinding&#45;&gt;remotion -->
<g id="edge3" class="edge">
<title>providerbinding&#45;&gt;remotion</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M540.68,-336.59C641.43,-289.13 771.93,-227.65 877.16,-178.08"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="878.21,-180.49 883.88,-174.92 875.98,-175.74 878.21,-180.49"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="741.94,-240 741.94,-262.8 850.65,-262.8 850.65,-240 741.94,-240"/>
<text xml:space="preserve" text-anchor="start" x="744.94" y="-245.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">routes POC only</text>
</g>
</g>
</svg>
`;default:throw Error(`Unknown viewId: `+e)}};export{e as dotSource,t as svgSource};