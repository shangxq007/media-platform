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
    mediaplatform [height=2.5,
        label=<<TABLE BORDER="0" CELLPADDING="0" CELLSPACING="4"><TR><TD><FONT POINT-SIZE="20">media-platform</FONT></TD></TR><TR><TD><FONT POINT-SIZE="15" COLOR="#bfdbfe">Render platform</FONT></TD></TR></TABLE>>,
        likec4_id=mediaPlatform,
        likec4_level=0,
        margin="0.223,0.223",
        width=4.445];
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
        style=dashed];
    platformapp -> aimodule [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">uses</FONT></TD></TR></TABLE>>,
        likec4_id=oyorty,
        style=dashed];
    rendermodule -> sharedkernel [arrowhead=normal,
        label=<<TABLE BORDER="0" CELLPADDING="3" CELLSPACING="0" BGCOLOR="#18191BA0"><TR><TD ALIGN="TEXT" BALIGN="LEFT"><FONT POINT-SIZE="14">depends on</FONT></TD></TR></TABLE>>,
        likec4_id="86h9qg",
        style=dashed];
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
<svg width="350pt" height="210pt"
 viewBox="0.00 0.00 350.00 210.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 195.05)">
<!-- mediaplatform -->
<g id="node1" class="node">
<title>mediaplatform</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="320.04,-180 0,-180 0,0 320.04,0 320.04,-180"/>
<text xml:space="preserve" text-anchor="start" x="93.33" y="-93" font-family="Arial" font-size="20.00" fill="#eff6ff">media&#45;platform</text>
<text xml:space="preserve" text-anchor="start" x="106.25" y="-70" font-family="Arial" font-size="15.00" fill="#bfdbfe">Render platform</text>
</g>
</g>
</svg>
`;case`containerDiagram`:return`<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN"
 "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<!-- Generated by graphviz version 14.1.5 (0)
 -->
<!-- Pages: 1 -->
<svg width="876pt" height="973pt"
 viewBox="0.00 0.00 876.00 973.00" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink">
<g id="graph0" class="graph" transform="scale(1 1) rotate(0) translate(15.05 957.85)">
<g id="clust1" class="cluster">
<title>cluster_mediaplatform</title>
<polygon fill="#194b9e" stroke="#1b3d88" points="8,-8 8,-934.8 838,-934.8 838,-8 8,-8"/>
<text xml:space="preserve" text-anchor="start" x="16" y="-921.9" font-family="Arial" font-weight="bold" font-size="11.00" fill="#bfdbfe" fill-opacity="0.701961">MEDIA&#45;PLATFORM</text>
</g>
<!-- platformapp -->
<g id="node1" class="node">
<title>platformapp</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="513.02,-873.6 192.98,-873.6 192.98,-693.6 513.02,-693.6 513.02,-873.6"/>
<text xml:space="preserve" text-anchor="start" x="296.86" y="-786.6" font-family="Arial" font-size="20.00" fill="#eff6ff">platform&#45;app</text>
<text xml:space="preserve" text-anchor="start" x="276.7" y="-763.6" font-family="Arial" font-size="15.00" fill="#bfdbfe">Spring Boot entry point</text>
</g>
<!-- rendermodule -->
<g id="node2" class="node">
<title>rendermodule</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="368.02,-550.8 47.98,-550.8 47.98,-370.8 368.02,-370.8 368.02,-550.8"/>
<text xml:space="preserve" text-anchor="start" x="142.97" y="-463.8" font-family="Arial" font-size="20.00" fill="#eff6ff">render&#45;module</text>
<text xml:space="preserve" text-anchor="start" x="141.3" y="-440.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">Core render domain</text>
</g>
<!-- aimodule -->
<g id="node3" class="node">
<title>aimodule</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="798.02,-550.8 477.98,-550.8 477.98,-370.8 798.02,-370.8 798.02,-550.8"/>
<text xml:space="preserve" text-anchor="start" x="594.09" y="-463.8" font-family="Arial" font-size="20.00" fill="#eff6ff">ai&#45;module</text>
<text xml:space="preserve" text-anchor="start" x="593.81" y="-440.8" font-family="Arial" font-size="15.00" fill="#bfdbfe">AI integration</text>
</g>
<!-- sharedkernel -->
<g id="node4" class="node">
<title>sharedkernel</title>
<polygon fill="#3b82f6" stroke="#2563eb" stroke-width="0" points="524.02,-228 203.98,-228 203.98,-48 524.02,-48 524.02,-228"/>
<text xml:space="preserve" text-anchor="start" x="302.86" y="-141" font-family="Arial" font-size="20.00" fill="#eff6ff">shared&#45;kernel</text>
<text xml:space="preserve" text-anchor="start" x="279.38" y="-118" font-family="Arial" font-size="15.00" fill="#bfdbfe">Shared domain primitives</text>
</g>
<!-- platformapp&#45;&gt;rendermodule -->
<g id="edge1" class="edge">
<title>platformapp&#45;&gt;rendermodule</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M312.8,-693.67C294.06,-652.21 271.7,-602.74 252.46,-560.16"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="254.88,-559.14 249.4,-553.39 250.09,-561.3 254.88,-559.14"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="284.85,-610.8 284.85,-633.6 320.42,-633.6 320.42,-610.8 284.85,-610.8"/>
<text xml:space="preserve" text-anchor="start" x="287.85" y="-616.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">uses</text>
</g>
<!-- platformapp&#45;&gt;aimodule -->
<g id="edge2" class="edge">
<title>platformapp&#45;&gt;aimodule</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M432.01,-693.67C469.38,-651.6 514.08,-601.28 552.28,-558.29"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="554.05,-560.24 557.07,-552.89 550.13,-556.76 554.05,-560.24"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="504.05,-610.8 504.05,-633.6 539.62,-633.6 539.62,-610.8 504.05,-610.8"/>
<text xml:space="preserve" text-anchor="start" x="507.05" y="-616.6" font-family="Arial" font-size="14.00" fill="#c9c9c9">uses</text>
</g>
<!-- rendermodule&#45;&gt;sharedkernel -->
<g id="edge3" class="edge">
<title>rendermodule&#45;&gt;sharedkernel</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M251.25,-370.87C271.45,-329.32 295.56,-279.73 316.3,-237.1"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="318.56,-238.44 319.48,-230.55 313.84,-236.15 318.56,-238.44"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="290.68,-288 290.68,-310.8 369.86,-310.8 369.86,-288 290.68,-288"/>
<text xml:space="preserve" text-anchor="start" x="293.68" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">depends on</text>
</g>
<!-- aimodule&#45;&gt;sharedkernel -->
<g id="edge4" class="edge">
<title>aimodule&#45;&gt;sharedkernel</title>
<path fill="none" stroke="#8d8d8d" stroke-width="2" stroke-dasharray="5,2" d="M562.04,-370.87C526.18,-328.88 483.31,-278.69 446.64,-235.76"/>
<polygon fill="#8d8d8d" stroke="#8d8d8d" stroke-width="2" points="448.7,-234.13 441.84,-230.13 444.71,-237.54 448.7,-234.13"/>
<polygon fill="#18191b" fill-opacity="0.627451" stroke="none" points="509.22,-288 509.22,-310.8 588.39,-310.8 588.39,-288 509.22,-288"/>
<text xml:space="preserve" text-anchor="start" x="512.22" y="-293.8" font-family="Arial" font-size="14.00" fill="#c9c9c9">depends on</text>
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
`;default:throw Error(`Unknown viewId: `+e)}};export{e as dotSource,t as svgSource};